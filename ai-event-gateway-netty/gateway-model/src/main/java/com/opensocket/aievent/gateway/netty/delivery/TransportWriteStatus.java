package com.opensocket.aievent.gateway.netty.delivery;

/** Low-level socket write result used by CommandDeliveryService. */
public enum TransportWriteStatus {
    SENT,
    NOT_WRITABLE,
    TIMEOUT,
    FAILED
}
