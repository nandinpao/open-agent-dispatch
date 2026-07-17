package com.opensocket.aievent.gateway.netty.admin.dto;

import java.time.OffsetDateTime;

/**
 * Immutable DTO for Netty transport-gateway status responses.
 *
 * <p>P6.1.1 intentionally removes task lifecycle counters from this DTO. Formal task state belongs
 * to ai-event-gateway-core / control-plane, not ai-event-gateway-netty.</p>
 */
public record GatewayStatusResponse(
        String nodeId,
        String environment,
        String version,
        String status,
        boolean tcpEnabled,
        String tcpHost,
        int tcpPort,
        long tcpActiveConnections,
        boolean websocketEnabled,
        String websocketHost,
        int websocketPort,
        long websocketActiveSessions,
        long websocketAgentSessions,
        long websocketAdminSessions,
        long agentTotal,
        long agentIdle,
        long agentBusy,
        long agentOffline,
        long agentTimeout,
        long agentHeartbeatTimeoutSeconds,
        boolean clusterEnabled,
        String clusterUdpHost,
        int clusterUdpPort,
        String clusterBroadcastHost,
        int clusterBroadcastPort,
        long clusterTotalNodes,
        long clusterOnlineNodes,
        long clusterSuspectNodes,
        long clusterOfflineNodes,
        long clusterHeartbeatIntervalMs,
        long clusterSuspectTimeoutMs,
        long clusterOfflineTimeoutMs,
        OffsetDateTime serverTime
) {
}
