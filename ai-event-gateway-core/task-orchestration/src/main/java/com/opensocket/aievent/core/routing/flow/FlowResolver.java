package com.opensocket.aievent.core.routing.flow;

import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Thin Source Flow / Rule resolver facade for RoutingDecisionService.
 *
 * <p>Phase 3-5 centralizes flow-rule runtime repair, routing policy selection,
 * and Source Flow pool-first task classification while preserving the previous
 * behavior of RoutingDecisionService.</p>
 */
public class FlowResolver {
    private final RuleResolver ruleResolver;

    public FlowResolver(RoutingProperties properties, FlowRuleRoutingService flowRuleRoutingService) {
        this.ruleResolver = new RuleResolver(properties, flowRuleRoutingService);
    }

    public FlowResolution resolve(TaskRecord task) {
        TaskRecord repaired = ruleResolver.applyRuntimeRepair(task);
        boolean flowRuleTask = ruleResolver.isFlowRuleTask(repaired);
        RoutingPolicy policy = flowRuleTask ? RoutingPolicy.FLOW_RULE : RoutingPolicy.MANUAL_REVIEW;
        boolean sourceFlowPoolFirstTask = ruleResolver.isSourceFlowPoolFirstTask(repaired);
        return new FlowResolution(repaired, policy, flowRuleTask, sourceFlowPoolFirstTask);
    }

    public boolean isFlowRuleTask(TaskRecord task) {
        return ruleResolver.isFlowRuleTask(task);
    }

    public boolean isSourceFlowPoolFirstTask(TaskRecord task) {
        return ruleResolver.isSourceFlowPoolFirstTask(task);
    }

    public String decisionSuffix(TaskRecord task) {
        return ruleResolver.decisionSuffix(task);
    }

    public java.util.List<String> requiredSkills(TaskRecord task) {
        return ruleResolver.requiredSkills(task);
    }
}
