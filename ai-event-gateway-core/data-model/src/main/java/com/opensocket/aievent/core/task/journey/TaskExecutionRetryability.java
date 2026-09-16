package com.opensocket.aievent.core.task.journey;

/** Explicit retry semantics; UNKNOWN is used when the authority does not expose a retry contract. */
public enum TaskExecutionRetryability {
    NOT_APPLICABLE,
    RETRYABLE,
    NOT_RETRYABLE,
    MANUAL_RECONCILIATION,
    UNKNOWN
}
