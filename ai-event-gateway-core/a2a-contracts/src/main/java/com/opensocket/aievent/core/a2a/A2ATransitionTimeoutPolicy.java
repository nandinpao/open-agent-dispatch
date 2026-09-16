package com.opensocket.aievent.core.a2a;

public enum A2ATransitionTimeoutPolicy {
    NONE,
    REQUEST_EXPIRY,
    APPROVAL_EXPIRY,
    CHILD_TASK_CREATION_TIMEOUT,
    DISPATCH_TIMEOUT,
    RESULT_TIMEOUT,
    CANCELLATION_ACK_TIMEOUT,
    RECONCILIATION_BACKOFF
}
