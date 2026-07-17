package com.opensocket.aievent.gateway.netty.admin.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Immutable DTO for Admin API responses or Admin WebSocket events. These records are optimized
 * for React/Next.js dashboard consumption.
 */
public record AdminEventPayload(
        String eventId,
        String nodeId,
        String eventType,
        String message,
        Map<String, Object> data,
        OffsetDateTime occurredAt
) {
}
