package com.opensocket.aievent.gateway.netty.admin.runtime.dto;

import com.opensocket.aievent.gateway.netty.agent.AgentStatus;
import com.opensocket.aievent.gateway.netty.agent.ConnectionType;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentResponse;
import com.opensocket.aievent.gateway.netty.authorization.AgentAuthorizationContext;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent connection view that separates transport status, agent-reported status,
 * runtime freshness, and Core authorization context.
 */
public record RuntimeAgentConnectionResponse(
        String agentId,
        String agentType,
        ConnectionType connectionType,
        String gatewayNodeId,
        String transportStatus,
        String reportedStatus,
        String freshnessStatus,
        Long connectionAgeMs,
        Long heartbeatAgeMs,
        Long heartbeatTimeoutMs,
        Boolean heartbeatStale,
        List<String> capabilities,
        OffsetDateTime registeredAt,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime statusUpdatedAt,
        String remoteAddress,
        String connectionId,
        String sessionId,
        Map<String, Object> metadata,
        Boolean coreAuthorized,
        String coreApprovalStatus,
        String coreRiskStatus,
        Integer credentialVersion,
        Integer policyVersion,
        OffsetDateTime coreAuthorizedAt,
        Boolean clusterRemote,
        String clusterSyncStatus
) {
    private static final long DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 30L;

    public static RuntimeAgentConnectionResponse from(AgentResponse agent) {
        return from(agent, DEFAULT_HEARTBEAT_TIMEOUT_SECONDS, null);
    }

    public static RuntimeAgentConnectionResponse from(AgentResponse agent, long heartbeatTimeoutSeconds) {
        return from(agent, heartbeatTimeoutSeconds, null);
    }

    public static RuntimeAgentConnectionResponse from(
            AgentResponse agent,
            long heartbeatTimeoutSeconds,
            AgentAuthorizationContext authorization
    ) {
        var now = OffsetDateTime.now();
        long normalizedTimeoutSeconds = heartbeatTimeoutSeconds <= 0 ? DEFAULT_HEARTBEAT_TIMEOUT_SECONDS : heartbeatTimeoutSeconds;
        long timeoutMs = normalizedTimeoutSeconds * 1000L;
        Long heartbeatAgeMs = elapsedMs(agent.lastHeartbeatAt(), now);
        Long connectionAgeMs = elapsedMs(agent.registeredAt(), now);
        boolean stale = heartbeatAgeMs != null && heartbeatAgeMs > timeoutMs;
        return new RuntimeAgentConnectionResponse(
                agent.agentId(),
                agent.agentType() == null ? "UNKNOWN" : agent.agentType().name(),
                agent.connectionType(),
                agent.gatewayNodeId(),
                transportStatus(agent.status()),
                agent.status() == null ? "UNKNOWN" : agent.status().name(),
                freshnessStatus(agent.status(), agent.lastHeartbeatAt(), heartbeatAgeMs, timeoutMs),
                connectionAgeMs,
                heartbeatAgeMs,
                timeoutMs,
                stale,
                agent.capabilities() == null ? List.of() : List.copyOf(agent.capabilities()),
                agent.registeredAt(),
                agent.lastHeartbeatAt(),
                agent.statusUpdatedAt(),
                agent.remoteAddress(),
                agent.connectionId(),
                agent.sessionId(),
                agent.metadata() == null ? Map.of() : Map.copyOf(agent.metadata()),
                authorization == null ? null : authorization.enabled(),
                authorization == null ? null : authorization.approvalStatus(),
                authorization == null ? null : authorization.riskStatus(),
                authorization == null ? null : authorization.credentialVersion(),
                authorization == null ? null : authorization.policyVersion(),
                authorization == null ? null : authorization.authorizedAt(),
                false,
                "LOCAL"
        );
    }

    public static RuntimeAgentConnectionResponse from(AgentResponse agent, String fallbackNodeId, boolean self, String clusterSyncStatus) {
        var local = from(agent);
        return new RuntimeAgentConnectionResponse(
                local.agentId(),
                local.agentType(),
                local.connectionType(),
                local.gatewayNodeId() == null || local.gatewayNodeId().isBlank() ? fallbackNodeId : local.gatewayNodeId(),
                local.transportStatus(),
                local.reportedStatus(),
                local.freshnessStatus(),
                local.connectionAgeMs(),
                local.heartbeatAgeMs(),
                local.heartbeatTimeoutMs(),
                local.heartbeatStale(),
                local.capabilities(),
                local.registeredAt(),
                local.lastHeartbeatAt(),
                local.statusUpdatedAt(),
                local.remoteAddress(),
                local.connectionId(),
                local.sessionId(),
                local.metadata(),
                local.coreAuthorized(),
                local.coreApprovalStatus(),
                local.coreRiskStatus(),
                local.credentialVersion(),
                local.policyVersion(),
                local.coreAuthorizedAt(),
                !self,
                clusterSyncStatus == null || clusterSyncStatus.isBlank() ? (self ? "LOCAL" : "UNKNOWN") : clusterSyncStatus
        );
    }

    private static String transportStatus(AgentStatus status) {
        if (status == AgentStatus.OFFLINE || status == AgentStatus.DISCONNECTED) {
            return "DISCONNECTED";
        }
        if (status == AgentStatus.TIMEOUT) {
            return "TIMEOUT";
        }
        if (status == null) {
            return "UNKNOWN";
        }
        return "CONNECTED";
    }

    private static String freshnessStatus(AgentStatus status, OffsetDateTime lastHeartbeatAt, Long heartbeatAgeMs, long timeoutMs) {
        if (status == AgentStatus.OFFLINE || status == AgentStatus.DISCONNECTED) {
            return "DISCONNECTED";
        }
        if (status == AgentStatus.TIMEOUT) {
            return "TIMEOUT";
        }
        if (status == null || lastHeartbeatAt == null || heartbeatAgeMs == null) {
            return "UNKNOWN";
        }
        if (heartbeatAgeMs > timeoutMs) {
            return "STALE";
        }
        return "FRESH";
    }

    private static Long elapsedMs(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            return null;
        }
        long value = Duration.between(from, to).toMillis();
        return Math.max(value, 0L);
    }
}
