package com.opensocket.aievent.gateway.netty.agent.dto;

import com.opensocket.aievent.gateway.netty.agent.AgentSnapshot;
import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.AgentType;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Immutable DTO for agent-related protocol payloads. These records represent data received from
 * or returned to Agent clients over TCP, WebSocket, or REST APIs.
 */
public record AgentResponse(
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
    public static AgentResponse from(AgentSnapshot snapshot) {
        return new AgentResponse(
                snapshot.agentId(),
                snapshot.agentType(),
                snapshot.connectionType(),
                snapshot.gatewayNodeId(),
                snapshot.status(),
                snapshot.capabilities(),
                snapshot.currentTaskId(),
                snapshot.registeredAt(),
                snapshot.lastHeartbeatAt(),
                snapshot.statusUpdatedAt(),
                snapshot.remoteAddress(),
                snapshot.connectionId(),
                snapshot.sessionId(),
                snapshot.metadata()
        );
    }
}
