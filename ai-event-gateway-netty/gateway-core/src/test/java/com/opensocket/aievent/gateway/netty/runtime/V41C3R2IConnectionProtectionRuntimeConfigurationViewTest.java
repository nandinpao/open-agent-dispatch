package com.opensocket.aievent.gateway.netty.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.config.ConnectionProtectionProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationLocalRegistry;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshot;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2IConnectionProtectionRuntimeConfigurationViewTest {
    @Test
    void shouldPreferRuntimeLimitsButKeepSecurityEnableOutsideRuntimeView() {
        ConnectionProtectionProperties startup = new ConnectionProtectionProperties();
        startup.setMaxTcpConnections(100);
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        ConnectionProtectionRuntimeConfigurationView view = new ConnectionProtectionRuntimeConfigurationView(startup, values);
        assertThat(view.maxTcpConnections()).isEqualTo(100);
        registry.atomicSwap(snapshot("cp-r1", "{\"connection-protection.max-tcp-connections\":250,\"connection-protection.close-on-rate-limit\":false}"));
        assertThat(view.maxTcpConnections()).isEqualTo(250);
        assertThat(view.closeOnRateLimit()).isFalse();
        assertThat(ConnectionProtectionRuntimeConfigurationView.ALL).doesNotContain("connection-protection.enabled");
    }

    @Test
    void shouldRejectUnsafeLimit() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        ConnectionProtectionRuntimeConfigurationView view = new ConnectionProtectionRuntimeConfigurationView(new ConnectionProtectionProperties(), values);
        registry.atomicSwap(snapshot("cp-invalid", "{\"connection-protection.max-tcp-connections\":0}"));
        assertThatThrownBy(view::maxTcpConnections).hasMessageContaining("RUNTIME_CONFIG_VALIDATION_FAILED");
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(String revision, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new GatewayRuntimeConfigurationSnapshot("LOCAL", "cp", ConnectionProtectionRuntimeConfigurationView.SET_KEY,
                revision, 1, 12, now, now.plusHours(1), "GATEWAY", payload, "hash-" + revision, "signature");
    }
}
