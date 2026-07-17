package com.opensocket.aievent.gateway.netty.admin.dto;

/**
 * Immutable DTO for Admin API responses or Admin WebSocket events. These records are optimized
 * for React/Next.js dashboard consumption.
 */
public record AdminWebSocketStatusResponse(
        int activeAdminChannels,
        long activeAdminSessions,
        long retainedEventCount,
        int retainedEventLimit
) {
}
