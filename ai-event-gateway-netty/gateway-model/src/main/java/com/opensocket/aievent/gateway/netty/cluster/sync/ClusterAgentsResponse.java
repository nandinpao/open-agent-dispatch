package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Cluster-wide Agent aggregation response for Admin UI.
 *
 * <p>The response groups local and remote snapshots by gateway node. It is intentionally
 * read-only and does not change dispatch ownership.</p>
 */
public record ClusterAgentsResponse(
        String localNodeId,
        long totalAgents,
        Map<String, Long> agentCountsByNode,
        Map<String, List<AgentResponse>> agentsByNode,
        List<ClusterAgentNodeGroupResponse> nodes,
        OffsetDateTime generatedAt
) {
}
