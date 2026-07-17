package com.opensocket.aievent.core.routing.governance;

import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.task.TaskRecord;

/** Calculates the generic requirement contract without mutating the Task or routing plan. */
public interface DispatchRequirementResolver {
    DispatchRequirementResolution resolve(TaskRecord task, FlowRuleRoutingPlan authoritativeLegacyPlan);
}
