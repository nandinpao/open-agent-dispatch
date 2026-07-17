package com.opensocket.aievent.gateway.netty.cluster;

import com.opensocket.aievent.gateway.netty.cluster.dto.ClusterHelloPayload;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import com.opensocket.aievent.gateway.netty.config.NettyServerProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterNodeRegistryTest {

    private final ClusterNodeRegistry registry = new ClusterNodeRegistry(
            new GatewayProperties("gateway-node-001", "test", "1.1.1-spec-alignment-test", "test gateway"),
            new NettyServerProperties(
                    new NettyServerProperties.Tcp(true, "0.0.0.0", 19090),
                    new NettyServerProperties.Websocket(true, "0.0.0.0", 19091),
                    new NettyServerProperties.Cluster(
                            true,
                            "UDP_BROADCAST",
                            "",
                            "0.0.0.0",
                            19100,
                            "255.255.255.255",
                            19100,
                            "gateway-node-001",
                            3000,
                            10000,
                            30000
                    )
            ),
            18080
    );

    @Test
    void shouldRegisterRemoteNodeFromHello() {
        var change = registry.applyHello(
                new ClusterHelloPayload(
                        "gateway-node-002",
                        "gateway-node-002",
                        19090,
                        19091,
                        18080,
                        19100,
                        OffsetDateTime.now()
                ),
                "/172.20.0.3:19100"
        );

        assertThat(change).isPresent();
        assertThat(change.orElseThrow().currentStatus()).isEqualTo(ClusterNodeStatus.ONLINE);
        assertThat(registry.count()).isEqualTo(2);
        assertThat(registry.countByStatus(ClusterNodeStatus.SELF)).isEqualTo(1);
        assertThat(registry.countByStatus(ClusterNodeStatus.ONLINE)).isEqualTo(1);
    }
}
