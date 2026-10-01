package com.opensocket.aievent.gateway.netty.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationLocalRegistry;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshot;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2CAgentRuntimeConfigurationViewTest {
    @Test
    void shouldPreferGatewayLocalSnapshotAndKeepStartupFallbackBeforeCutover() {
        AgentProperties startup = new AgentProperties(30, 5000);
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(
                registry, JsonMapper.builder().build());
        AgentRuntimeConfigurationView view = new AgentRuntimeConfigurationView(startup, values);

        assertThat(view.heartbeatTimeoutSeconds()).isEqualTo(30);
        assertThat(view.timeoutScanIntervalMs()).isEqualTo(5000);

        registry.atomicSwap(snapshot("agent-r1", 1,
                "{\"agent.heartbeat-timeout-seconds\":45,\"agent.timeout-scan-interval-ms\":2500}"));

        assertThat(view.heartbeatTimeoutSeconds()).isEqualTo(45);
        assertThat(view.timeoutScanIntervalMs()).isEqualTo(2500);
        assertThat(view.snapshotPresent()).isTrue();
    }

    @Test
    void shouldRejectUnsafeRuntimeBounds() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(
                registry, JsonMapper.builder().build());
        AgentRuntimeConfigurationView view = new AgentRuntimeConfigurationView(new AgentProperties(), values);
        registry.atomicSwap(snapshot("agent-invalid", 2,
                "{\"agent.heartbeat-timeout-seconds\":1,\"agent.timeout-scan-interval-ms\":100}"));
        assertThatThrownBy(view::heartbeatTimeout)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_VALIDATION_FAILED");
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new GatewayRuntimeConfigurationSnapshot(
                "LOCAL", "config-set-agent", AgentRuntimeConfigurationView.SET_KEY,
                revision, sequence, 7, now, now.plusHours(1), "GATEWAY", payload,
                "hash-" + sequence, "signature");
    }
}
