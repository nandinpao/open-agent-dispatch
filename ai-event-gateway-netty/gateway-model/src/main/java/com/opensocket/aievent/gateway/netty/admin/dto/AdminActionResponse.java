package com.opensocket.aievent.gateway.netty.admin.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Standard response shape for Admin UI action endpoints such as ping, disconnect, retry, drain,
 * resume, login, refresh, and logout.
 */
public record AdminActionResponse(
        String action,
        String targetType,
        String targetId,
        String status,
        String message,
        Map<String, Object> data,
        OffsetDateTime occurredAt
) {
    public static AdminActionResponse accepted(String action, String targetType, String targetId, String message) {
        return new AdminActionResponse(action, targetType, targetId, "ACCEPTED", message, Map.of(), OffsetDateTime.now());
    }

    public static AdminActionResponse completed(String action, String targetType, String targetId, String message, Map<String, Object> data) {
        return new AdminActionResponse(action, targetType, targetId, "COMPLETED", message, data == null ? Map.of() : data, OffsetDateTime.now());
    }
}
