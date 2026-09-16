package com.opensocket.aievent.core.a2a;

/**
 * Operational progress within an A2A lifecycle status.
 *
 * <p>The lifecycle status remains the business authority. This stage makes
 * in-flight work, blocking and recovery observable without creating a second
 * Task or Dispatch state machine.</p>
 */
public enum A2AOperationalStage {
    REQUESTED,
    VALIDATING,
    WAITING_APPROVAL,
    APPROVED,
    CHILD_TASK_CREATING,
    CHILD_TASK_CREATED,
    DISPATCH_REQUESTED,
    DISPATCHING,
    BLOCKED,
    RUNNING,
    WAITING_RESULT,
    CANCEL_REQUESTED,
    WAIT_HUMAN,
    TERMINAL;

    public static A2AOperationalStage defaultFor(A2ARequestStatus status) {
        if (status == null) {
            return REQUESTED;
        }
        return switch (status) {
            case REQUESTED -> REQUESTED;
            case VALIDATING -> VALIDATING;
            case WAITING_APPROVAL -> WAITING_APPROVAL;
            case APPROVED -> APPROVED;
            case CHILD_TASK_CREATED -> CHILD_TASK_CREATED;
            case DISPATCHING -> DISPATCHING;
            case RUNNING -> WAITING_RESULT;
            case CANCEL_REQUESTED -> CANCEL_REQUESTED;
            case CANCELLED_UNCONFIRMED -> BLOCKED;
            case WAIT_HUMAN -> WAIT_HUMAN;
            case REJECTED, COMPLETED, FAILED, CANCELLED_CONFIRMED, EXPIRED -> TERMINAL;
        };
    }
}
