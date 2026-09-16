package com.opensocket.aievent.core.a2a;

/** Mutable operational lifecycle around an immutable canonical Result. */
public enum A2AResultProcessingStatus {
    CHILD_COMPLETION_PENDING,
    CHILD_COMPLETED,
    PARENT_AGGREGATION_PENDING,
    COMPLETED,
    FAILED_RETRYABLE,
    WAIT_HUMAN
}
