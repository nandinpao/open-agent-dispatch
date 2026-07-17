package com.opensocket.aievent.gateway.netty.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreAgentSecurityEventPublisherTest {
    @Test
    void duplicateRuntimeEventContractCarriesClusterContext() {
        var event = new DuplicateRuntimeSecurityEvent(
                "agent-001",
                "gateway-node-001",
                java.util.List.of("gateway-node-001", "gateway-node-002"),
                2,
                java.util.List.of(java.util.Map.of("gatewayNodeId", "gateway-node-001"), java.util.Map.of("gatewayNodeId", "gateway-node-002")),
                "duplicate",
                "test",
                false,
                true,
                java.time.OffsetDateTime.now());
        assertThat(event.gatewayNodeIds()).containsExactly("gateway-node-001", "gateway-node-002");
        assertThat(event.connectedCount()).isEqualTo(2);
        assertThat(event.clusterDuplicate()).isTrue();
    }
}
