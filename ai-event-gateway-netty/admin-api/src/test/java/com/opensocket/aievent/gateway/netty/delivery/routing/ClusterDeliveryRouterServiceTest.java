package com.opensocket.aievent.gateway.netty.delivery.routing;

import com.opensocket.aievent.gateway.netty.agent.AgentRegistry;
import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.cluster.sync.ClusterRemoteStateRegistry;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.config.DeliveryRouterProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterDeliveryRouterServiceTest {

    @Test
    void routeDecisionReturnsNotFoundWhenAgentIsUnknown() {
        var service = newService(new AgentRegistry(gatewayProperties()));

        var decision = service.routeDecision("missing-agent");

        assertThat(decision.routeType()).isEqualTo("NOT_FOUND");
        assertThat(decision.candidateGatewayNodeIds()).isEmpty();
    }

    @Test
    void routeDecisionPrefersLocalConnectedAgent() {
        var gatewayProperties = gatewayProperties();
        var agentRegistry = new AgentRegistry(gatewayProperties);
        agentRegistry.register(
                new AgentRegisterPayload("agent-local-001", AgentType.OPENCLAW, ConnectionType.TCP, List.of("task"), Map.of()),
                ConnectionType.TCP,
                "tcp-local-001",
                null,
                "127.0.0.1"
        );
        var service = newService(agentRegistry);

        var decision = service.routeDecision("agent-local-001");

        assertThat(decision.routeType()).isEqualTo("LOCAL");
        assertThat(decision.targetGatewayNodeId()).isEqualTo("gateway-node-001");
        assertThat(decision.candidateGatewayNodeIds()).containsExactly("gateway-node-001");
    }

    private ClusterDeliveryRouterService newService(AgentRegistry agentRegistry) {
        return new ClusterDeliveryRouterService(
                gatewayProperties(),
                new DeliveryRouterProperties(true, true, true, true, 3000),
                new AdminProperties(),
                agentRegistry,
                new ClusterRemoteStateRegistry(new ClusterSyncProperties(true, 5000, 2000, 15000, 50, 50)),
                null,
                null
        );
    }

    private GatewayProperties gatewayProperties() {
        return new GatewayProperties(
                "gateway-node-001",
                "test",
                "test",
                "test gateway",
                "LOCAL",
                "Local Site",
                "local",
                "local-zone"
        );
    }
}
