package com.opensocket.aievent.gateway.netty.delivery;

import com.opensocket.aievent.gateway.netty.protocol.MessageType;

import java.util.Map;

/**
 * Command delivery request accepted by ai-event-gateway-netty from a future Core / Control Plane.
 * Netty does not interpret payload semantics; it only serializes the command and delivers it to a
 * locally connected Agent.
 */
public record CommandDeliveryRequest(
        String commandId,
        MessageType messageType,
        Map<String, Object> payload,
        String traceId,
        String issuedBy,
        Long timeoutMs
) {
    private static final long DEFAULT_TIMEOUT_MS = 3000L;
    private static final long MIN_TIMEOUT_MS = 100L;
    private static final long MAX_TIMEOUT_MS = 30000L;

    public CommandDeliveryRequest {
        if (messageType == null) {
            messageType = MessageType.TASK_DISPATCH;
        }
        if (payload == null) {
            payload = Map.of();
        }
        if (timeoutMs == null) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
        timeoutMs = Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, timeoutMs));
    }

    public CommandDeliveryRequest(
            String commandId,
            MessageType messageType,
            Map<String, Object> payload,
            String traceId,
            String issuedBy
    ) {
        this(commandId, messageType, payload, traceId, issuedBy, DEFAULT_TIMEOUT_MS);
    }
}
