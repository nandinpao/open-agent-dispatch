package com.opensocket.aievent.gateway.netty.cluster.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationLocalRegistry;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshot;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2JClusterSyncRuntimeConfigurationViewTest {
    @Test
    void shouldPreferRuntimeClusterSyncValues() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        ClusterSyncRuntimeConfigurationView view = new ClusterSyncRuntimeConfigurationView(
                new ClusterSyncProperties(false, 5000, 2000, 15000, 50, 50), values);
        registry.atomicSwap(snapshot("cluster-r1", "{\"cluster.sync.enabled\":true,\"cluster.sync.interval-ms\":7000,\"cluster.sync.request-timeout-ms\":4500,\"cluster.sync.remote-state-ttl-ms\":22000,\"cluster.sync.max-agents-per-node\":77,\"cluster.sync.max-events-per-node\":88}"));
        assertThat(view.enabled()).isTrue();
        assertThat(view.intervalMs()).isEqualTo(7000);
        assertThat(view.requestTimeoutMs()).isEqualTo(4500);
        assertThat(view.remoteStateTtlMs()).isEqualTo(22000);
        assertThat(view.maxAgentsPerNode()).isEqualTo(77);
        assertThat(view.maxEventsPerNode()).isEqualTo(88);
        assertThat(ClusterSyncRuntimeConfigurationView.ALL).hasSize(6);
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(String revision, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new GatewayRuntimeConfigurationSnapshot("LOCAL", "cluster", ClusterSyncRuntimeConfigurationView.SET_KEY,
                revision, 1, 12, now, now.plusHours(1), "GATEWAY", payload, "hash-" + revision, "signature");
    }
}
