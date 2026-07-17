package com.opensocket.aievent.gateway.netty.cluster.dto;

import java.util.Map;

/**
 * Immutable DTO for cluster discovery and monitoring payloads. These records are used by UDP
 * discovery, internal state sync, and Admin REST APIs.
 */
public record ClusterSummaryResponse(
        long totalNodes,
        long onlineNodes,
        long suspectNodes,
        long offlineNodes,
        long discoveredNodes,
        boolean clusterEnabled,
        String udpHost,
        int udpPort,
        String broadcastHost,
        int broadcastPort,
        long heartbeatIntervalMs,
        long suspectTimeoutMs,
        long offlineTimeoutMs,
        Map<String, Long> byStatus
) {
}
