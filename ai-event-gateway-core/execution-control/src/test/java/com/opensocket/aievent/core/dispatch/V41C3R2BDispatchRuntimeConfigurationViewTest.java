package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2BDispatchRuntimeConfigurationViewTest {
    @Test
    void shouldExposeCompleteDispatchSnapshotBeforeCutoverAndFailClosedAfterCutover() {
        DispatchProperties startup = new DispatchProperties();
        startup.setWorkerId("startup-worker");
        startup.setSourceNodeId("startup-node");
        startup.getClient().setMaxBatchSize(25);
        startup.getClient().setAutoExecuteIntervalMs(5000);

        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        DispatchRuntimeConfigurationView view = new DispatchRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("dispatch-r1", 1, "{" +
                "\"dispatch.claim-lease\":\"PT45S\"," +
                "\"dispatch.client.auto-execute-interval-ms\":2500," +
                "\"dispatch.client.connect-timeout\":\"PT2S\"," +
                "\"dispatch.client.default-gateway-base-url\":\"https://gateway.example.internal\"," +
                "\"dispatch.client.gateway-base-urls.gateway-tpe-001\":\"https://tpe.example.internal\"," +
                "\"dispatch.client.gateway-base-urls.gateway-tyn-001\":\"https://tyn.example.internal\"," +
                "\"dispatch.client.gateway-base-urls.gateway-tnn-001\":\"https://tnn.example.internal\"," +
                "\"dispatch.client.max-batch-size\":64," +
                "\"dispatch.client.request-timeout\":\"PT8S\"," +
                "\"dispatch.execution-policy\":\"MANUAL_HOLD\"," +
                "\"dispatch.gateway-dispatch-path\":\"/internal/delivery/agents/{agentId}/commands\"," +
                "\"dispatch.require-assignable-agent\":false," +
                "\"dispatch.review-mode\":\"MANUAL_REVIEW\"," +
                "\"dispatch.source-node-id\":\"runtime-node\"," +
                "\"dispatch.worker-id\":\"runtime-worker\"}"));

        assertThat(view.claimLease().toSeconds()).isEqualTo(45);
        assertThat(view.autoExecuteInterval().toMillis()).isEqualTo(2500);
        assertThat(view.connectTimeout().toSeconds()).isEqualTo(2);
        assertThat(view.requestTimeout().toSeconds()).isEqualTo(8);
        assertThat(view.maxBatchSize()).isEqualTo(64);
        assertThat(view.executionPolicy()).isEqualTo(DispatchExecutionPolicy.MANUAL_HOLD);
        assertThat(view.reviewMode()).isEqualTo(DispatchReviewMode.MANUAL_REVIEW);
        assertThat(view.requireAssignableAgent()).isFalse();
        assertThat(view.sourceNodeId()).isEqualTo("runtime-node");
        assertThat(view.workerId()).isEqualTo("runtime-worker");
        assertThat(view.gatewayBaseUrl("gateway-tyn-001")).isEqualTo("https://tyn.example.internal");
        assertThat(view.revisionId()).isEqualTo("dispatch-r1");

        authority.activate(Set.of(DispatchRuntimeConfigurationView.CLIENT_MAX_BATCH_SIZE));
        registry.atomicSwap(snapshot("dispatch-r2", 2, "{\"dispatch.execution-policy\":\"PAUSED\"}"));

        assertThatThrownBy(view::maxBatchSize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE")
                .hasMessageContaining(DispatchRuntimeConfigurationView.CLIENT_MAX_BATCH_SIZE);
    }

    @Test
    void shouldRejectInvalidRuntimeEnum() {
        DispatchProperties startup = new DispatchProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        DispatchRuntimeConfigurationView view = new DispatchRuntimeConfigurationView(startup, values, new RuntimeConfigurationAuthorityRegistry());

        registry.atomicSwap(snapshot("dispatch-invalid", 3, "{\"dispatch.execution-policy\":\"NOT_A_POLICY\"}"));
        assertThatThrownBy(view::executionPolicy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_VALIDATION_FAILED")
                .hasMessageContaining(DispatchRuntimeConfigurationView.EXECUTION_POLICY);
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 9, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-dispatch", RuntimeConfigurationSetKeys.DISPATCH_SYSTEM,
                revision, sequence, 6, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
