package com.opensocket.aievent.core.task;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Central transition guard for TODO 15-B Task Lifecycle State Model.
 *
 * <p>This guard is intentionally permissive around legacy states so existing persisted tasks can
 * keep moving while new flows migrate toward the canonical TODO 15 states.</p>
 */
public final class TaskLifecycleTransitionGuard {
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = new EnumMap<>(TaskStatus.class);

    static {
        allow(TaskStatus.DRAFT,
                TaskStatus.READY, TaskStatus.WAITING_APPROVAL, TaskStatus.WAITING_CONTEXT,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.READY,
                TaskStatus.WAITING_APPROVAL, TaskStatus.WAITING_CONTEXT, TaskStatus.DISPATCHING, TaskStatus.QUEUED,
                TaskStatus.BLOCKED, TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_APPROVAL,
                TaskStatus.READY, TaskStatus.QUEUED, TaskStatus.BLOCKED,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_CONTEXT,
                TaskStatus.READY, TaskStatus.QUEUED, TaskStatus.BLOCKED,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.DISPATCHING,
                TaskStatus.QUEUED, TaskStatus.ASSIGNED, TaskStatus.RETRY_WAIT,
                TaskStatus.BLOCKED, TaskStatus.FAILED, TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_DEPENDENCY,
                TaskStatus.READY, TaskStatus.QUEUED, TaskStatus.RUNNING,
                TaskStatus.WAITING_HUMAN, TaskStatus.BLOCKED, TaskStatus.FAILED,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.WAITING_HUMAN,
                TaskStatus.READY, TaskStatus.QUEUED, TaskStatus.RUNNING,
                TaskStatus.PARTIALLY_COMPLETED, TaskStatus.SUCCEEDED, TaskStatus.COMPLETED, TaskStatus.FAILED,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.PARTIALLY_COMPLETED,
                TaskStatus.RUNNING, TaskStatus.WAITING_DEPENDENCY, TaskStatus.WAITING_HUMAN,
                TaskStatus.SUCCEEDED, TaskStatus.COMPLETED, TaskStatus.FAILED,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.BLOCKED,
                TaskStatus.READY, TaskStatus.QUEUED, TaskStatus.RETRY_WAIT,
                TaskStatus.WAITING_HUMAN, TaskStatus.FAILED, TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.CANCEL_REQUESTED,
                TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED);

        allow(TaskStatus.QUEUED,
                TaskStatus.ASSIGNED, TaskStatus.RUNNING,
                TaskStatus.RETRY_WAIT, TaskStatus.WAITING_DEPENDENCY, TaskStatus.WAITING_HUMAN, TaskStatus.BLOCKED,
                TaskStatus.FAILED, TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);
        allow(TaskStatus.CREATED,
                TaskStatus.READY, TaskStatus.ASSIGNED, TaskStatus.DISPATCHED, TaskStatus.RUNNING,
                TaskStatus.WAITING_DEPENDENCY, TaskStatus.WAITING_HUMAN, TaskStatus.BLOCKED,
                TaskStatus.RETRY_WAIT, TaskStatus.FAILED, TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);


        allow(TaskStatus.ASSIGNED,
                TaskStatus.RUNNING, TaskStatus.SUCCEEDED, TaskStatus.COMPLETED,
                TaskStatus.FAILED, TaskStatus.RETRY_WAIT, TaskStatus.DEAD_LETTER, TaskStatus.ORPHANED,
                TaskStatus.RECONCILING, TaskStatus.DISPATCHED, TaskStatus.WAITING_DEPENDENCY, TaskStatus.WAITING_HUMAN,
                TaskStatus.PARTIALLY_COMPLETED, TaskStatus.CANCEL_REQUESTED, TaskStatus.TIMED_OUT, TaskStatus.CANCELLED);
        allow(TaskStatus.DISPATCHED,
                TaskStatus.RUNNING, TaskStatus.SUCCEEDED, TaskStatus.COMPLETED,
                TaskStatus.FAILED, TaskStatus.RETRY_WAIT, TaskStatus.DEAD_LETTER, TaskStatus.ORPHANED,
                TaskStatus.RECONCILING, TaskStatus.WAITING_DEPENDENCY, TaskStatus.WAITING_HUMAN,
                TaskStatus.PARTIALLY_COMPLETED, TaskStatus.CANCEL_REQUESTED, TaskStatus.TIMED_OUT, TaskStatus.CANCELLED);

        allow(TaskStatus.RUNNING,
                TaskStatus.WAITING_DEPENDENCY, TaskStatus.WAITING_HUMAN, TaskStatus.PARTIALLY_COMPLETED,
                TaskStatus.SUCCEEDED, TaskStatus.COMPLETED, TaskStatus.FAILED,
                TaskStatus.RETRY_WAIT, TaskStatus.DEAD_LETTER, TaskStatus.ORPHANED, TaskStatus.RECONCILING,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.TIMED_OUT, TaskStatus.CANCELLED);

        allow(TaskStatus.RETRY_WAIT,
                TaskStatus.QUEUED, TaskStatus.CREATED,
                TaskStatus.ASSIGNED, TaskStatus.FAILED, TaskStatus.ESCALATED, TaskStatus.DEAD_LETTER,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.CANCELLED);

        allow(TaskStatus.FAILED, TaskStatus.RETRY_WAIT, TaskStatus.ESCALATED, TaskStatus.DEAD_LETTER, TaskStatus.RECONCILING);
        allow(TaskStatus.ORPHANED, TaskStatus.RECONCILING, TaskStatus.QUEUED, TaskStatus.CREATED, TaskStatus.FAILED, TaskStatus.DEAD_LETTER);
        allow(TaskStatus.RECONCILING, TaskStatus.QUEUED, TaskStatus.CREATED, TaskStatus.ASSIGNED, TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.DEAD_LETTER);
    }

    private TaskLifecycleTransitionGuard() {
    }

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        if (to == null) {
            return false;
        }
        if (from == null) {
            return to == TaskStatus.DRAFT || to == TaskStatus.READY || to == TaskStatus.QUEUED || to == TaskStatus.CREATED || to == TaskStatus.SUPPRESSED;
        }
        if (from == to) {
            return true;
        }
        if (from.isTerminal() && from != TaskStatus.FAILED && from != TaskStatus.ORPHANED && from != TaskStatus.RECONCILING) {
            return false;
        }
        Set<TaskStatus> allowed = ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static void requireTransition(TaskStatus from, TaskStatus to, String taskId) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal task lifecycle transition taskId=" + taskId
                    + " from=" + from + " to=" + to);
        }
    }

    public static Set<TaskStatus> canonicalStates() {
        return EnumSet.of(
                TaskStatus.DRAFT, TaskStatus.READY, TaskStatus.WAITING_APPROVAL, TaskStatus.WAITING_CONTEXT,
                TaskStatus.DISPATCHING, TaskStatus.QUEUED, TaskStatus.ASSIGNED,
                TaskStatus.RUNNING, TaskStatus.WAITING_DEPENDENCY, TaskStatus.WAITING_HUMAN,
                TaskStatus.PARTIALLY_COMPLETED, TaskStatus.RETRY_WAIT, TaskStatus.BLOCKED,
                TaskStatus.CANCEL_REQUESTED, TaskStatus.SUCCEEDED, TaskStatus.COMPLETED, TaskStatus.FAILED,
                TaskStatus.ESCALATED, TaskStatus.DEAD_LETTER, TaskStatus.EXPIRED,
                TaskStatus.CANCELLED, TaskStatus.ORPHANED, TaskStatus.RECONCILING);
    }

    private static void allow(TaskStatus from, TaskStatus... targets) {
        ALLOWED.put(from, EnumSet.copyOf(java.util.List.of(targets)));
    }
}
