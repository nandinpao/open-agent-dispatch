package com.opensocket.aievent.gateway.netty.authorization;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Security observation emitted by Netty when the same logical Agent identity is observed
 * in more than one active runtime session. This is an observation, not a routing decision;
 * Core remains the governance authority that decides quarantine / credential rotation.
 */
public record DuplicateRuntimeSecurityEvent(
        String agentId,
        String gatewayNodeId,
        List<String> gatewayNodeIds,
        int connectedCount,
        List<Map<String, Object>> sessions,
        String reason,
        String detectedBy,
        boolean localDuplicate,
        boolean clusterDuplicate,
        OffsetDateTime detectedAt
) {
    public DuplicateRuntimeSecurityEvent {
        gatewayNodeIds = gatewayNodeIds == null ? List.of() : List.copyOf(gatewayNodeIds);
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        detectedAt = detectedAt == null ? OffsetDateTime.now() : detectedAt;
    }
}
