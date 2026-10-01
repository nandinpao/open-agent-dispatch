package com.opensocket.aievent.worker.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.worker.AdapterWorkerProperties;

import tools.jackson.databind.json.JsonMapper;

/** C3R2E proof for Adapter Worker local snapshot consumers. */
class V41C3R2EAdapterWorkerRuntimeConfigurationViewTest {
    @Test
    void snapshotControlsWorkerExecutionInputs() {
        AdapterWorkerProperties startup = new AdapterWorkerProperties();
        WorkerRuntimeConfigurationLocalRegistry registry = new WorkerRuntimeConfigurationLocalRegistry();
        AdapterWorkerRuntimeConfigurationView view = new AdapterWorkerRuntimeConfigurationView(startup, registry, JsonMapper.builder().build());
        registry.atomicSwap(snapshot("w1", 1, """
                {"adapter-worker.enabled":false,
                 "adapter-worker.adapter-types":"MCP",
                 "adapter-worker.lease-seconds":90,
                 "adapter-worker.poll-interval-ms":1750,
                 "adapter-worker.request-timeout":"PT12S"}
                """, "hash-w1"));

        assertThat(view.enabled()).isFalse();
        assertThat(view.adapterTypes()).containsExactly("MCP");
        assertThat(view.leaseSeconds()).isEqualTo(90);
        assertThat(view.pollInterval()).hasMillis(1750);
        assertThat(view.requestTimeout()).hasSeconds(12);
        assertThat(view.revisionId()).isEqualTo("w1");
    }

    @Test
    void issueTrackingCannotBeEnabledOnGenericExternalWorker() {
        AdapterWorkerProperties startup = new AdapterWorkerProperties();
        WorkerRuntimeConfigurationLocalRegistry registry = new WorkerRuntimeConfigurationLocalRegistry();
        AdapterWorkerRuntimeConfigurationView view = new AdapterWorkerRuntimeConfigurationView(startup, registry, JsonMapper.builder().build());
        registry.atomicSwap(snapshot("w2", 2, "{\"adapter-worker.adapter-types\":\"MCP,ISSUE_TRACKING\"}", "hash-w2"));

        assertThatThrownBy(view::adapterTypes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_VALIDATION_FAILED")
                .hasMessageContaining(AdapterWorkerRuntimeConfigurationView.ADAPTER_TYPES);
    }

    @Test
    void requiredWorkerKeyCannotFallBackToStartupValue() {
        AdapterWorkerProperties startup = new AdapterWorkerProperties();
        WorkerRuntimeConfigurationLocalRegistry registry = new WorkerRuntimeConfigurationLocalRegistry();
        AdapterWorkerRuntimeConfigurationView view = new AdapterWorkerRuntimeConfigurationView(startup, registry, JsonMapper.builder().build());
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        registry.atomicSwap(new WorkerRuntimeConfigurationSnapshot("LOCAL","config-set-adapter-worker",AdapterWorkerRuntimeConfigurationView.SET_KEY,
                "x3",3,3,now,now.plusHours(1),"WORKER","DUAL_READ",java.util.Set.of(AdapterWorkerRuntimeConfigurationView.LEASE_SECONDS),
                "{}","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","signature"));
        assertThatThrownBy(view::leaseSeconds).isInstanceOf(IllegalStateException.class).hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    private static WorkerRuntimeConfigurationSnapshot snapshot(String revision,long sequence,String payload,String hash) {
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        return new WorkerRuntimeConfigurationSnapshot("LOCAL","config-set-adapter-worker",AdapterWorkerRuntimeConfigurationView.SET_KEY,
                revision,sequence,2,now,now.plusHours(1),"ADAPTER_WORKER",payload,hash,"signature");
    }
}
