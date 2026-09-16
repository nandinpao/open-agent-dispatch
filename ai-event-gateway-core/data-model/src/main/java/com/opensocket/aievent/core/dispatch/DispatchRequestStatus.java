package com.opensocket.aievent.core.dispatch;

public enum DispatchRequestStatus {
    PENDING_REVIEW,
    APPROVED,
    SUPPRESSED,
    REJECTED,
    CANCELLED,
    DISPATCHING,
    /** Network send started but the transport outcome is not yet known. Automatic resend is forbidden. */
    DELIVERY_UNKNOWN,
    DISPATCHED,
    ACKED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    RETRY_WAITING,
    DEAD_LETTER
}
