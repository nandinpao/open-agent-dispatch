package com.opensocket.aievent.gateway.netty.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Lightweight transport trace response synthesized from retained Admin events.
 *
 * <p>P6.1.1 removes task-state correlation from Netty traces; task traces belong to Core.</p>
 */
public record AdminTraceResponse(
        String traceId,
        List<AdminEventPayload> events,
        Map<String, Object> summary,
        OffsetDateTime generatedAt
) {
}
