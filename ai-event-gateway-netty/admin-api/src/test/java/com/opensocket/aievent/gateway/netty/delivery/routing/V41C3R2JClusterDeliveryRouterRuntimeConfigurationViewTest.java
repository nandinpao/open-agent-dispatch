package com.opensocket.aievent.gateway.netty.delivery.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.config.DeliveryRouterProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationLocalRegistry;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshot;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2JClusterDeliveryRouterRuntimeConfigurationViewTest {
    @Test
    void shouldPreferRuntimeDeliveryRouterValues() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        ClusterDeliveryRouterRuntimeConfigurationView view = new ClusterDeliveryRouterRuntimeConfigurationView(
                new DeliveryRouterProperties(false, true, true, true, 3000), values);
        registry.atomicSwap(snapshot("router-r1", "{\"cluster.delivery-router.enabled\":true,\"cluster.delivery-router.prefer-local\":false,\"cluster.delivery-router.reject-duplicate-agents\":false,\"cluster.delivery-router.require-synced-remote-state\":false,\"cluster.delivery-router.request-timeout-ms\":6200}"));
        assertThat(view.enabled()).isTrue();
        assertThat(view.preferLocal()).isFalse();
        assertThat(view.rejectDuplicateAgents()).isFalse();
        assertThat(view.requireSyncedRemoteState()).isFalse();
        assertThat(view.requestTimeoutMs()).isEqualTo(6200);
        assertThat(ClusterDeliveryRouterRuntimeConfigurationView.ALL).hasSize(5);
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(String revision, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new GatewayRuntimeConfigurationSnapshot("LOCAL", "cluster", ClusterDeliveryRouterRuntimeConfigurationView.SET_KEY,
                revision, 1, 12, now, now.plusHours(1), "GATEWAY", payload, "hash-" + revision, "signature");
    }
}
