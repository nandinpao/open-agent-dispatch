package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;

/** Outbound port implemented by Execution Control for dispatch lifecycle mutation. */
public interface TaskExecutionLifecyclePort {
    boolean cancelOpenDispatchByAssignment(String assignmentId, String reason, OffsetDateTime now);
    static TaskExecutionLifecyclePort noop() { return (assignmentId, reason, now) -> false; }
}
