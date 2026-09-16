package com.opensocket.aievent.core.routing.flow;

import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Resolved Source Flow / Rule runtime view used by the Pool-first routing path.
 */
public record FlowResolution(
        TaskRecord task,
        RoutingPolicy policy,
        boolean flowRuleTask,
        boolean sourceFlowPoolFirstTask,
        String flowVersion
) {
}
