package com.opensocket.aievent.gateway.netty.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationLocalRegistry;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshot;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2IGatewayOperationalRuntimeConfigurationViewTest {
    @Test
    void shouldPreferRuntimeOperationalValues() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        GatewayOperationalRuntimeConfigurationView view = new GatewayOperationalRuntimeConfigurationView(
                new CoreAgentAuthorizationProperties(), new CoreDirectorySyncProperties(), new CoreForwardProperties(),
                new CoreOutboundProperties(), new CoreTaskCallbackRelayProperties(), values);
        registry.atomicSwap(snapshot("gateway-r1", "{\"gateway.agent-authorization.timeout-ms\":4200,\"gateway.core-forward.enabled\":true,\"gateway.core-forward.history-limit\":750,\"gateway.core-outbound.enabled\":false}"));
        assertThat(view.agentAuthorizationTimeoutMs()).isEqualTo(4200);
        assertThat(view.coreForwardEnabled()).isTrue();
        assertThat(view.coreForwardHistoryLimit()).isEqualTo(750);
        assertThat(view.coreOutboundEnabled()).isFalse();
        assertThat(GatewayOperationalRuntimeConfigurationView.ALL).hasSize(24);
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(String revision, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new GatewayRuntimeConfigurationSnapshot("LOCAL", "gateway", GatewayOperationalRuntimeConfigurationView.SET_KEY,
                revision, 1, 12, now, now.plusHours(1), "GATEWAY", payload, "hash-" + revision, "signature");
    }
}
