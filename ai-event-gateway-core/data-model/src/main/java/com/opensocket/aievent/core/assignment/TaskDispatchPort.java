package com.opensocket.aievent.core.assignment;

import com.opensocket.aievent.core.dispatch.DispatchDecisionResult;
import com.opensocket.aievent.core.task.TaskRecord;

/** Outbound port implemented by the execution-control assembly. */
public interface TaskDispatchPort {
    DispatchDecisionResult createIfEligible(TaskAssignment assignment, TaskRecord task);
}
