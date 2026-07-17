package com.opensocket.aievent.gateway.netty.admin.dto;

import java.time.OffsetDateTime;

/**
 * Standard Admin WebSocket event format consumed by the React/Next.js dashboard.
 *
 * <p>P2 uses this same flat shape for both persisted operational events and transient metric
 * events. The payload type differs by event: persisted events carry {@link AdminEventPayload},
 * while metric events carry the metric DTO directly.</p>
 */
public record AdminRealtimeEvent<T>(
        String eventType,
        OffsetDateTime timestamp,
        String nodeId,
        T payload
) {
}
