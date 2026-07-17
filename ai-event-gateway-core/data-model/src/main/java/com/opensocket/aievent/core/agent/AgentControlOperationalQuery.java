package com.opensocket.aievent.core.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.opensocket.aievent.core.gateway.GatewayNode;
import com.opensocket.aievent.core.gateway.GatewayNodeQuery;

/** Read-only operational boundary for Agent and Gateway directory state. */
public interface AgentControlOperationalQuery {
    Optional<AgentSnapshot> findAgent(String agentId);
    List<AgentSnapshot> searchAgents(AgentQuery query);
    Optional<GatewayNode> findGatewayNode(String gatewayNodeId);
    List<GatewayNode> searchGatewayNodes(GatewayNodeQuery query);
    Map<String, Integer> agentStatusCounts(int limit);
    Map<String, Integer> gatewayStatusCounts(int limit);
    String agentStoreMode();
    String gatewayStoreMode();
}
