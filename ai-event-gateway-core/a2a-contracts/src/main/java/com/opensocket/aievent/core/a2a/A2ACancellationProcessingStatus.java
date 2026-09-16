package com.opensocket.aievent.core.a2a;

/** Operational lifecycle kept separate from the cancellation business outcome. */
public enum A2ACancellationProcessingStatus {
    REQUESTED,
    FENCING_ROTATED,
    RUNTIME_DELIVERY_PENDING,
    RUNTIME_ACK_PENDING,
    CONFIRMED,
    FAILED_RETRYABLE,
    WAIT_HUMAN
}
