package com.opensocket.aievent.gateway.netty.agent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent domain component for Agent Snapshot. It manages the lifecycle, status, connection
 * identity, and query model used by the Admin UI and command delivery diagnostics.
 */
public record AgentSnapshot(
        String agentId,
        AgentType agentType,
        ConnectionType connectionType,
        String gatewayNodeId,
        AgentStatus status,
        List<String> capabilities,
        String currentTaskId,
        OffsetDateTime registeredAt,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime statusUpdatedAt,
        String remoteAddress,
        String connectionId,
        String sessionId,
        Map<String, Object> metadata
) {
}
