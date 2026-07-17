package com.opensocket.aievent.core.dispatch;

public enum DispatchRequestStatus {
    PENDING_REVIEW,
    APPROVED,
    SUPPRESSED,
    REJECTED,
    CANCELLED,
    DISPATCHING,
    DISPATCHED,
    ACKED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    RETRY_WAITING,
    DEAD_LETTER
}
