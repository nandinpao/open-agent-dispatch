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
 * <p>Legacy states are retained only for older task rows while current standard dispatch uses direct delivery.</p>
 */
public enum TaskStatus {
    /** Governance draft that is not yet eligible for dispatch. */
    DRAFT,
    /** Governance-ready Task; it may enter approval or the runtime queue. */
    READY,
    /** Task requires an explicit approval decision. */
    WAITING_APPROVAL,
    /** Task exists but cannot be dispatched until an approved Handoff Context Snapshot is available. */
    WAITING_CONTEXT,
    /** OpenDispatch is resolving a Pool and Agent. */
    DISPATCHING,
    /** Task is waiting for a Parent, Child or referenced dependency. */
    WAITING_DEPENDENCY,
    /** Task is waiting for an operator or external human decision. */
    WAITING_HUMAN,
    /** Some required outcomes completed, but the Task is not fully complete. */
    PARTIALLY_COMPLETED,
    /** Cancellation was requested but runtime fencing/acknowledgement is pending. */
    CANCEL_REQUESTED,
    /** Task can no longer execute because its validity window ended. */
    EXPIRED,
    /** Task cannot progress until a diagnosed blocker is resolved. */
    BLOCKED,

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
    /** Task completed according to the governance lifecycle. */
    COMPLETED,
    /** @deprecated use {@link #FAILED}, {@link #RETRY_WAIT}, or {@link #ORPHANED} based on policy. */
    @Deprecated
    TIMED_OUT,
    /** Task cancellation completed and runtime work is fenced. */
    CANCELLED;

    private static final Set<TaskStatus> TERMINAL_STATUSES = EnumSet.of(
            SUCCEEDED, FAILED, ESCALATED, DEAD_LETTER, EXPIRED,
            SUPPRESSED, COMPLETED, TIMED_OUT, CANCELLED);

    private static final Set<TaskStatus> ACTIVE_STATUSES = EnumSet.complementOf(EnumSet.copyOf(TERMINAL_STATUSES));

    private static final Set<TaskStatus> DISPATCH_READY_STATUSES = EnumSet.of(READY, QUEUED, CREATED, RETRY_WAIT);

    private static final Set<TaskStatus> CALLBACK_ELIGIBLE_STATUSES = EnumSet.of(
            READY, DISPATCHING, QUEUED, CREATED, ASSIGNED, DISPATCHED, RUNNING, RETRY_WAIT, RECONCILING);

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
        return this == FAILED || this == TIMED_OUT || this == DEAD_LETTER || this == ESCALATED || this == EXPIRED || this == CANCELLED;
    }

    /** Returns the TODO 15 canonical equivalent for legacy states. */
    public TaskStatus canonical() {
        return switch (this) {
            case READY, CREATED -> QUEUED;
            case DISPATCHING -> QUEUED;
            case DISPATCHED -> ASSIGNED;
            case TIMED_OUT -> FAILED;
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
