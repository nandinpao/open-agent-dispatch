package com.opensocket.aievent.core.capability.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2GA2ADelegationRuntimeConfigurationViewTest {
    @Test
    void activeA2AControlsUseSnapshotAndFailClosedAfterAuthorityCutover() {
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        MockEnvironment startup = new MockEnvironment().withProperty("opendispatch.a2a-async.batch-size", "11");
        A2ADelegationRuntimeConfigurationView view = new A2ADelegationRuntimeConfigurationView(values, authority, startup);

        assertThat(view.asyncBatchSize()).isEqualTo(11);
        registry.atomicSwap(snapshot("a2a-r1", 1, payload()));
        assertThat(view.asyncEnabled()).isFalse();
        assertThat(view.asyncBatchSize()).isEqualTo(25);
        assertThat(view.asyncPollDelay().toMillis()).isEqualTo(4000);
        assertThat(view.pushMaxBodyBytes()).isEqualTo(2_097_152);
        assertThat(view.planRuntimePollDelay().toMillis()).isEqualTo(1800);

        authority.activate(Set.of(A2ADelegationRuntimeConfigurationView.ASYNC_ENABLED));
        registry.atomicSwap(snapshot("a2a-r2", 2, "{}"));
        assertThatThrownBy(view::asyncEnabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_SNAPSHOT_INCOMPLETE");
    }

    private static String payload() {
        return "{" +
                "\"a2a.push.ingress.max-body-bytes\":2097152," +
                "\"a2a.reconciliation.pipeline-ms\":35000," +
                "\"opendispatch.a2a-async.enabled\":false," +
                "\"opendispatch.a2a-async.batch-size\":25," +
                "\"opendispatch.a2a-async.poll-ms\":4000," +
                "\"opendispatch.a2a-async.push-handoff-batch-size\":24," +
                "\"opendispatch.a2a-async.push-handoff-ms\":1200," +
                "\"opendispatch.a2a-read.enabled\":true," +
                "\"opendispatch.a2a-read.batch-size\":22," +
                "\"opendispatch.a2a-read.poll-ms\":2300," +
                "\"opendispatch.capability-result-retry.enabled\":true," +
                "\"opendispatch.capability-result-retry.batch-size\":60," +
                "\"opendispatch.capability-result-retry.poll-ms\":5500," +
                "\"opendispatch.mcp-read.enabled\":true," +
                "\"opendispatch.mcp-read.batch-size\":21," +
                "\"opendispatch.mcp-read.poll-ms\":2100," +
                "\"opendispatch.plan-runtime.batch-size\":23," +
                "\"opendispatch.plan-runtime.poll-ms\":1800," +
                "\"opendispatch.a2a-authority-revocation-sweep-ms\":32000}";
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 24, 1, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-a2a", A2ADelegationRuntimeConfigurationView.SET_KEY,
                revision, sequence, 7, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
