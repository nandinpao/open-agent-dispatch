package com.opensocket.aievent.core.a2a;

/** Authoritative business outcome of a cancellation attempt. */
public enum A2ACancellationOutcome {
    PENDING,
    CANCELLED_CONFIRMED,
    CANCELLED_UNCONFIRMED,
    CANCELLATION_TIMEOUT,
    AGENT_ALREADY_COMPLETED,
    STALE_CANCELLATION
}
