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
        allow(TaskStatus.QUEUED,
                TaskStatus.ASSIGNED, TaskStatus.RUNNING,
                TaskStatus.RETRY_WAIT, TaskStatus.FAILED, TaskStatus.CANCELLED);
        allow(TaskStatus.CREATED,
                TaskStatus.ASSIGNED, TaskStatus.DISPATCHED, TaskStatus.RUNNING,
                TaskStatus.RETRY_WAIT, TaskStatus.FAILED, TaskStatus.CANCELLED);


        allow(TaskStatus.ASSIGNED,
                TaskStatus.RUNNING, TaskStatus.SUCCEEDED, TaskStatus.COMPLETED,
                TaskStatus.FAILED, TaskStatus.RETRY_WAIT, TaskStatus.DEAD_LETTER, TaskStatus.ORPHANED,
                TaskStatus.RECONCILING, TaskStatus.DISPATCHED, TaskStatus.TIMED_OUT, TaskStatus.CANCELLED);
        allow(TaskStatus.DISPATCHED,
                TaskStatus.RUNNING, TaskStatus.SUCCEEDED, TaskStatus.COMPLETED,
                TaskStatus.FAILED, TaskStatus.RETRY_WAIT, TaskStatus.DEAD_LETTER, TaskStatus.ORPHANED,
                TaskStatus.RECONCILING, TaskStatus.TIMED_OUT, TaskStatus.CANCELLED);

        allow(TaskStatus.RUNNING,
                TaskStatus.SUCCEEDED, TaskStatus.COMPLETED, TaskStatus.FAILED,
                TaskStatus.RETRY_WAIT, TaskStatus.DEAD_LETTER, TaskStatus.ORPHANED, TaskStatus.RECONCILING,
                TaskStatus.TIMED_OUT, TaskStatus.CANCELLED);

        allow(TaskStatus.RETRY_WAIT,
                TaskStatus.QUEUED, TaskStatus.CREATED,
                TaskStatus.ASSIGNED, TaskStatus.FAILED, TaskStatus.ESCALATED, TaskStatus.DEAD_LETTER,
                TaskStatus.CANCELLED);

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
            return to == TaskStatus.QUEUED || to == TaskStatus.CREATED || to == TaskStatus.SUPPRESSED;
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
                TaskStatus.QUEUED,
                TaskStatus.ASSIGNED,
                TaskStatus.RUNNING,
                TaskStatus.RETRY_WAIT,
                TaskStatus.SUCCEEDED,
                TaskStatus.FAILED,
                TaskStatus.ESCALATED,
                TaskStatus.DEAD_LETTER,
                TaskStatus.ORPHANED,
                TaskStatus.RECONCILING);
    }

    private static void allow(TaskStatus from, TaskStatus... targets) {
        ALLOWED.put(from, EnumSet.copyOf(java.util.List.of(targets)));
    }
}
