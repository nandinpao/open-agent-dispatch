package com.opensocket.aievent.gateway.netty.common.dto;

import java.util.Map;

public record GatewayAckPayload(
        String messageId,
        String messageType,
        String connectionId,
        String status,
        String message,
        Map<String, Object> evidence
) {
    public GatewayAckPayload {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }

    public GatewayAckPayload(String messageId, String messageType, String connectionId, String status, String message) {
        this(messageId, messageType, connectionId, status, message, Map.of());
    }
}
