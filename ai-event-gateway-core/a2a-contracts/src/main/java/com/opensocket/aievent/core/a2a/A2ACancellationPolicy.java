package com.opensocket.aievent.core.a2a;

public enum A2ACancellationPolicy {
    CANCEL_ALL_CHILDREN,
    CANCEL_PENDING_ONLY,
    DO_NOT_CANCEL_RUNNING,
    MANUAL_DECISION
}
