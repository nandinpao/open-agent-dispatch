package com.opensocket.aievent.core.task;

import java.util.EnumSet;
import java.util.Set;

/**
 * Canonical Task lifecycle states for TODO 15-B.
 *
 * <p>The canonical operational model is:</p>
 * <pre>
 * QUEUED -> ASSIGNED -> RUNNING -> SUCCEEDED
 *                     |          -> FAILED / RETRY_WAIT / ORPHANED / RECONCILING
 * RETRY_WAIT -> QUEUED
 * FAILED -> ESCALATED / DEAD_LETTER
 * ORPHANED -> RECONCILING -> QUEUED / FAILED / DEAD_LETTER
 * </pre>
 *
 * <p>Legacy states are retained only for older task rows while Stage 8 standard dispatch uses direct delivery.</p>
 */
public enum TaskStatus {
    /** Task is ready for dispatch eligibility/routing. */
    QUEUED,
    /** Task has a selected Agent/assignment lease. */
    ASSIGNED,
    /** Agent has started execution. */
    RUNNING,
    /** Task is waiting for delayed retry. */
    RETRY_WAIT,
    /** Task completed successfully. */
    SUCCEEDED,
    /** Task failed and may still be eligible for escalation or DLQ. */
    FAILED,
    /** Task has been escalated for human or higher-tier handling. */
    ESCALATED,
    /** Task is terminal in the failure queue / DLQ. */
    DEAD_LETTER,
    /** Task lost its owner assignment/session and requires reconciliation. */
    ORPHANED,
    /** Task is being reconciled after lease/session/callback ambiguity. */
    RECONCILING,

    /** @deprecated use {@link #QUEUED}. Retained for old rows and pre-15-B tests. */
    @Deprecated
    CREATED,
    /** @deprecated use timeline/suppression decision rather than a live task status. */
    @Deprecated
    SUPPRESSED,
    /** @deprecated use {@link #ASSIGNED} plus dispatch/attempt history. */
    @Deprecated
    DISPATCHED,
    /** @deprecated use {@link #SUCCEEDED}. */
    @Deprecated
    COMPLETED,
    /** @deprecated use {@link #FAILED}, {@link #RETRY_WAIT}, or {@link #ORPHANED} based on policy. */
    @Deprecated
    TIMED_OUT,
    /** @deprecated retained for operator-cancelled legacy tasks. */
    @Deprecated
    CANCELLED;

    private static final Set<TaskStatus> TERMINAL_STATUSES = EnumSet.of(
            SUCCEEDED, FAILED, ESCALATED, DEAD_LETTER,
            SUPPRESSED, COMPLETED, TIMED_OUT, CANCELLED);

    private static final Set<TaskStatus> ACTIVE_STATUSES = EnumSet.complementOf(EnumSet.copyOf(TERMINAL_STATUSES));

    private static final Set<TaskStatus> DISPATCH_READY_STATUSES = EnumSet.of(QUEUED, CREATED, RETRY_WAIT);

    private static final Set<TaskStatus> CALLBACK_ELIGIBLE_STATUSES = EnumSet.of(
            QUEUED, CREATED, ASSIGNED, DISPATCHED, RUNNING, RETRY_WAIT, RECONCILING);

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public boolean isActive() {
        return ACTIVE_STATUSES.contains(this);
    }

    public boolean isDispatchReady() {
        return DISPATCH_READY_STATUSES.contains(this);
    }

    public boolean isCallbackEligible() {
        return CALLBACK_ELIGIBLE_STATUSES.contains(this);
    }

    public boolean isSucceeded() {
        return this == SUCCEEDED || this == COMPLETED;
    }

    public boolean isFailed() {
        return this == FAILED || this == TIMED_OUT || this == DEAD_LETTER || this == ESCALATED || this == CANCELLED;
    }

    /** Returns the TODO 15 canonical equivalent for legacy states. */
    public TaskStatus canonical() {
        return switch (this) {
            case CREATED -> QUEUED;
            case DISPATCHED -> ASSIGNED;
            case COMPLETED -> SUCCEEDED;
            case TIMED_OUT, CANCELLED -> FAILED;
            default -> this;
        };
    }

    public static TaskStatus fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return TaskStatus.valueOf(value.trim());
    }
}
