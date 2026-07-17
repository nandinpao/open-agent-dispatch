package com.opensocket.aievent.gateway.netty.common.dto;

public record GatewayAckPayload(
        String messageId,
        String messageType,
        String connectionId,
        String status,
        String message
) {
}
