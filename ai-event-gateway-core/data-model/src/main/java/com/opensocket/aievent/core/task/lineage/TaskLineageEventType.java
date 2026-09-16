package com.opensocket.aievent.core.task.lineage;

/** Immutable lineage milestones. */
public enum TaskLineageEventType {
    TASK_CREATED,
    A2A_DELEGATED,
    EXECUTOR_ASSIGNED,
    EXECUTOR_REASSIGNED,
    EXECUTION_FAILED,
    EXECUTION_COMPLETED
}
