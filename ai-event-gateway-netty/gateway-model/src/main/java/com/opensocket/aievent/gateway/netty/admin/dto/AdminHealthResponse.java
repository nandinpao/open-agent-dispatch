package com.opensocket.aievent.gateway.netty.admin.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Health view dedicated to the Admin UI. It is intentionally smaller than the full gateway status
 * response so the dashboard can quickly decide whether the backend is reachable and usable.
 */
public record AdminHealthResponse(
        String status,
        String nodeId,
        String environment,
        String version,
        boolean tcpEnabled,
        boolean websocketEnabled,
        boolean clusterEnabled,
        Map<String, Object> checks,
        OffsetDateTime serverTime
) {
}
