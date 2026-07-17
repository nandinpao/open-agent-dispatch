package com.opensocket.aievent.gateway.netty.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterRuntimePropertiesTest {

    @Test
    void shouldPreferDirectDockerEnvironmentVariablesForClusterSettings() {
        var properties = new NettyServerProperties(
                new NettyServerProperties.Tcp(true, "0.0.0.0", 19090),
                new NettyServerProperties.Websocket(true, "0.0.0.0", 19091),
                NettyServerProperties.Cluster.defaults()
        );
        var env = new MockEnvironment()
                .withProperty("GATEWAY_CLUSTER_ENABLED", "true")
                .withProperty("GATEWAY_CLUSTER_DISCOVERY_MODE", "STATIC_PEERS")
                .withProperty("GATEWAY_CLUSTER_ANNOUNCE_HOST", "gateway-node-001")
                .withProperty("GATEWAY_CLUSTER_STATIC_PEERS",
                        "gateway-node-002@gateway-node-002:18080:19090:19091:19100," +
                                "gateway-node-003@gateway-node-003:18080:19090:19091:19100")
                .withProperty("CLUSTER_INTERNAL_TOKEN", "local-cluster-token");

        var runtime = new ClusterRuntimeProperties(properties, env);

        assertThat(runtime.enabled()).isTrue();
        assertThat(runtime.staticPeersEnabled()).isTrue();
        assertThat(runtime.announceHost()).isEqualTo("gateway-node-001");
        assertThat(runtime.internalToken()).isEqualTo("local-cluster-token");
        assertThat(runtime.parsedStaticPeers()).hasSize(2);
        assertThat(runtime.parsedStaticPeers().getFirst().nodeId()).isEqualTo("gateway-node-002");
    }
    @Test
    void springShouldAutowirePrimaryConstructorWhenTestConstructorAlsoExists() {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("cluster-test", Map.of(
                "GATEWAY_CLUSTER_ENABLED", "true",
                "GATEWAY_CLUSTER_DISCOVERY_MODE", "STATIC_PEERS",
                "GATEWAY_CLUSTER_ANNOUNCE_HOST", "gateway-node-001",
                "GATEWAY_CLUSTER_STATIC_PEERS", "gateway-node-002@gateway-node-002:18080:19090:19091:19100"
        )));
        context.registerBean(NettyServerProperties.class, () -> new NettyServerProperties(
                new NettyServerProperties.Tcp(true, "0.0.0.0", 19090),
                new NettyServerProperties.Websocket(true, "0.0.0.0", 19091),
                NettyServerProperties.Cluster.defaults()
        ));
        context.register(ClusterRuntimeProperties.class);

        context.refresh();

        var runtime = context.getBean(ClusterRuntimeProperties.class);
        assertThat(runtime.enabled()).isTrue();
        assertThat(runtime.staticPeersEnabled()).isTrue();
        assertThat(runtime.announceHost()).isEqualTo("gateway-node-001");
        assertThat(runtime.parsedStaticPeers()).hasSize(1);

        context.close();
    }

}
