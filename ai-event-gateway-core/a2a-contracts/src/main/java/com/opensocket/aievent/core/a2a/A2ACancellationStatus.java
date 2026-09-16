package com.opensocket.aievent.core.a2a;

public enum A2ACancellationStatus {
    REQUESTED,
    DELIVERY_PENDING,
    DELIVERED,
    ACKNOWLEDGED,
    TIMED_OUT,
    DISCONNECTED,
    FAILED,
    WAIT_HUMAN
}
