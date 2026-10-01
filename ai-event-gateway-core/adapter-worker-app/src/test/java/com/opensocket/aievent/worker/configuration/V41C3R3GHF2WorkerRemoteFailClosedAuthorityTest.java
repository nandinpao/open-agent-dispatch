package com.opensocket.aievent.worker.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.worker.AdapterWorkerProperties;

import tools.jackson.databind.json.JsonMapper;

class V41C3R3GHF2WorkerRemoteFailClosedAuthorityTest {
    @Test
    void strictWorkerAuthorityRejectsMissingSnapshotInsteadOfUsingStartupYaml() {
        AdapterWorkerProperties startup = new AdapterWorkerProperties();
        startup.setLeaseSeconds(120);
        AdapterWorkerRuntimeConfigurationView view = new AdapterWorkerRuntimeConfigurationView(
                startup, new WorkerRuntimeConfigurationLocalRegistry(), JsonMapper.builder().build(), true);

        assertThatThrownBy(view::leaseSeconds)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIGURATION_SNAPSHOT_MISSING")
                .hasMessageContaining(AdapterWorkerRuntimeConfigurationView.SET_KEY);
    }

    @Test
    void dualReadSnapshotStillPermitsStartupFallbackForNonRequiredKey() {
        AdapterWorkerProperties startup = new AdapterWorkerProperties();
        startup.setLeaseSeconds(120);
        WorkerRuntimeConfigurationLocalRegistry registry = new WorkerRuntimeConfigurationLocalRegistry();
        AdapterWorkerRuntimeConfigurationView view = new AdapterWorkerRuntimeConfigurationView(
                startup, registry, JsonMapper.builder().build(), true);
        registry.atomicSwap(snapshot("DUAL_READ", Set.of(), "{}"));

        assertThat(view.leaseSeconds()).isEqualTo(120);
    }

    @Test
    void requiredWorkerKeyCannotReactivateStartupFallback() {
        AdapterWorkerProperties startup = new AdapterWorkerProperties();
        startup.setLeaseSeconds(120);
        WorkerRuntimeConfigurationLocalRegistry registry = new WorkerRuntimeConfigurationLocalRegistry();
        AdapterWorkerRuntimeConfigurationView view = new AdapterWorkerRuntimeConfigurationView(
                startup, registry, JsonMapper.builder().build(), true);
        registry.atomicSwap(snapshot("DUAL_READ", Set.of(AdapterWorkerRuntimeConfigurationView.LEASE_SECONDS), "{}"));

        assertThatThrownBy(view::leaseSeconds)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    @Test
    void runtimeOnlyWorkerValueWinsOverStartupYaml() {
        AdapterWorkerProperties startup = new AdapterWorkerProperties();
        startup.setLeaseSeconds(120);
        WorkerRuntimeConfigurationLocalRegistry registry = new WorkerRuntimeConfigurationLocalRegistry();
        AdapterWorkerRuntimeConfigurationView view = new AdapterWorkerRuntimeConfigurationView(
                startup, registry, JsonMapper.builder().build(), true);
        String key = AdapterWorkerRuntimeConfigurationView.LEASE_SECONDS;
        registry.atomicSwap(snapshot("RUNTIME_ONLY", Set.of(key), "{\"" + key + "\":90}"));

        assertThat(view.leaseSeconds()).isEqualTo(90);
    }

    private static WorkerRuntimeConfigurationSnapshot snapshot(String mode, Set<String> required, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new WorkerRuntimeConfigurationSnapshot(
                "LOCAL", "worker", AdapterWorkerRuntimeConfigurationView.SET_KEY, "hf2-r1", 1, 13,
                now, now.plusHours(1), "WORKER", mode, required, payload,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "signature");
    }
}
