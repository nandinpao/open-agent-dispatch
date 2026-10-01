package com.opensocket.aievent.gateway.netty.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationLocalRegistry;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshot;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2INettyOperationalRuntimeConfigurationViewTest {
    @Test
    void shouldUseRuntimeTimingAndKeepInitialDelayAsStartupOnly() {
        MockEnvironment environment = new MockEnvironment().withProperty("netty.tcp.closed-connection-cleanup-initial-delay-ms", "7777");
        ClusterRuntimeProperties startup = new ClusterRuntimeProperties(new NettyServerProperties(null, null, null), environment);
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        NettyOperationalRuntimeConfigurationView view = new NettyOperationalRuntimeConfigurationView(startup, values, environment);
        registry.atomicSwap(snapshot("netty-r1", "{\"netty.cluster.heartbeat-interval-ms\":2500,\"netty.tcp.closed-connection-cleanup-interval-ms\":9000}"));
        assertThat(view.clusterHeartbeatIntervalMs()).isEqualTo(2500);
        assertThat(view.tcpCleanupIntervalMs()).isEqualTo(9000);
        assertThat(view.tcpCleanupInitialDelayMs()).isEqualTo(7777);
        assertThat(NettyOperationalRuntimeConfigurationView.ALL).doesNotContain("netty.tcp.closed-connection-cleanup-initial-delay-ms");
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(String revision, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new GatewayRuntimeConfigurationSnapshot("LOCAL", "netty", NettyOperationalRuntimeConfigurationView.SET_KEY,
                revision, 1, 12, now, now.plusHours(1), "GATEWAY", payload, "hash-" + revision, "signature");
    }
}
