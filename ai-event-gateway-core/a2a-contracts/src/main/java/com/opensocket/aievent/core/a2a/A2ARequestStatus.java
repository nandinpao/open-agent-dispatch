package com.opensocket.aievent.core.a2a;

public enum A2ARequestStatus {
    REQUESTED,
    VALIDATING,
    REJECTED,
    WAITING_APPROVAL,
    APPROVED,
    CHILD_TASK_CREATED,
    DISPATCHING,
    RUNNING,
    COMPLETED,
    FAILED,
    /** Cancellation intent was accepted; runtime acknowledgement is pending. */
    CANCEL_REQUESTED,
    /** Runtime or local authority proved the child can no longer execute. */
    CANCELLED_CONFIRMED,
    /** Cancellation was issued but no authoritative runtime acknowledgement arrived. */
    CANCELLED_UNCONFIRMED,
    /** Evidence is ambiguous and requires a governed operator decision. */
    WAIT_HUMAN,
    EXPIRED
}
