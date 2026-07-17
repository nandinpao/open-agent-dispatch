package com.opensocket.aievent.gateway.netty.delivery;

/**
 * Transport-level command delivery status. These values intentionally describe socket delivery only;
 * business task status belongs to ai-event-gateway-core / control-plane, not ai-event-gateway-netty.
 */
public enum DeliveryStatus {
    DELIVERED,
    AGENT_NOT_CONNECTED,
    AGENT_NOT_AUTHORIZED,
    MISSING_DISPATCH_CONTEXT,
    CONNECTION_NOT_WRITABLE,
    DELIVERY_TIMEOUT,
    INVALID_COMMAND,
    DELIVERY_FAILED
}
