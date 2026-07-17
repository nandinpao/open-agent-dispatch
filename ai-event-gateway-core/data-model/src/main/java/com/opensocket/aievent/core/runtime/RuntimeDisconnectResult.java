package com.opensocket.aievent.core.runtime;

import java.time.OffsetDateTime;
import java.util.Map;

public record RuntimeDisconnectResult(
        String agentId,
        String gatewayNodeId,
        String status,
        boolean requested,
        boolean closed,
        int httpStatus,
        String message,
        Map<String, Object> details,
        OffsetDateTime occurredAt
) {
    public static RuntimeDisconnectResult disabled(String agentId, String message) {
        return new RuntimeDisconnectResult(agentId, null, "DISABLED", false, false, 0, message, Map.of(), OffsetDateTime.now());
    }

    public static RuntimeDisconnectResult failed(String agentId, String gatewayNodeId, int httpStatus, String message) {
        return new RuntimeDisconnectResult(agentId, gatewayNodeId, "FAILED", true, false, httpStatus, message, Map.of(), OffsetDateTime.now());
    }
}
