package com.opensocket.aievent.core.task.v53;

import com.opensocket.aievent.core.task.TaskStatus;

/**
 * Stage 2 compatibility projection from the existing TaskStatus model into the
 * v5.3 lifecycle / phase / outcome model.
 *
 * <p>Important: this class is descriptive, not authoritative. During A0-R2,
 * callers must continue to use the existing TaskStatus transition authority.</p>
 */
public final class TaskStateProjection {
    public static final String VERSION = "V188_LEGACY_TASK_STATUS_V1";

    private TaskStateProjection() {
    }

    public static TaskStateSnapshot from(TaskStatus status) {
        if (status == null) {
            return snapshot(null, TaskLifecycle.ACTIVE, TaskPhase.EXECUTION, TaskOutcome.UNRESOLVED);
        }
        return switch (status) {
            case DRAFT -> snapshot(status, TaskLifecycle.CREATED, TaskPhase.INTAKE, TaskOutcome.UNRESOLVED);
            case WAITING_APPROVAL, WAITING_CONTEXT ->
                    snapshot(status, TaskLifecycle.WAITING, TaskPhase.ADMISSION, TaskOutcome.UNRESOLVED);
            case WAITING_DEPENDENCY ->
                    snapshot(status, TaskLifecycle.WAITING, TaskPhase.EXECUTION, TaskOutcome.UNRESOLVED);
            case WAITING_HUMAN, BLOCKED, CANCEL_REQUESTED ->
                    snapshot(status, TaskLifecycle.WAITING, TaskPhase.REMEDIATION, TaskOutcome.UNRESOLVED);
            case PARTIALLY_COMPLETED ->
                    snapshot(status, TaskLifecycle.ACTIVE, TaskPhase.AGGREGATION, TaskOutcome.UNRESOLVED);
            case ORPHANED, RECONCILING ->
                    snapshot(status, TaskLifecycle.ACTIVE, TaskPhase.REMEDIATION, TaskOutcome.UNRESOLVED);
            case SUCCEEDED, COMPLETED ->
                    snapshot(status, TaskLifecycle.CLOSED, TaskPhase.CLOSURE, TaskOutcome.SUCCEEDED);
            case CANCELLED, SUPPRESSED ->
                    snapshot(status, TaskLifecycle.CLOSED, TaskPhase.CLOSURE, TaskOutcome.CANCELLED);
            case FAILED, ESCALATED, DEAD_LETTER, EXPIRED, TIMED_OUT ->
                    snapshot(status, TaskLifecycle.CLOSED, TaskPhase.CLOSURE, TaskOutcome.FAILED);
            default -> snapshot(status, TaskLifecycle.ACTIVE, TaskPhase.EXECUTION, TaskOutcome.UNRESOLVED);
        };
    }

    private static TaskStateSnapshot snapshot(
            TaskStatus status,
            TaskLifecycle lifecycle,
            TaskPhase phase,
            TaskOutcome outcome) {
        return new TaskStateSnapshot(status, lifecycle, phase, outcome, VERSION);
    }
}
