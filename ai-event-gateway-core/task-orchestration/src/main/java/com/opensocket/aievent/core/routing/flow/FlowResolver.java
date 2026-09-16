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
        boolean governedPoolTask = ruleResolver.isGovernedPoolTask(repaired);
        RoutingPolicy policy = flowRuleTask
                ? RoutingPolicy.FLOW_RULE
                : governedPoolTask ? RoutingPolicy.GOVERNED_POOL : RoutingPolicy.MANUAL_REVIEW;
        boolean sourceFlowPoolFirstTask = ruleResolver.isSourceFlowPoolFirstTask(repaired);
        return new FlowResolution(repaired, policy, flowRuleTask, sourceFlowPoolFirstTask, null);
    }


    /** C7 simulation-only resolver. Production resolve() remains ACTIVE/ENABLED-only. */
    public FlowResolution resolveSimulation(TaskRecord task) {
        return resolveSimulation(task, java.util.Map.of());
    }

    public FlowResolution resolveSimulation(TaskRecord task, java.util.Map<String,Object> matchAttributes) {
        RuleResolver.SimulationRepairResult simulation = ruleResolver.applySimulationRepair(task, matchAttributes);
        TaskRecord repaired = simulation.task();
        boolean flowRuleTask = ruleResolver.isFlowRuleTask(repaired);
        boolean governedPoolTask = ruleResolver.isGovernedPoolTask(repaired);
        RoutingPolicy policy = flowRuleTask
                ? RoutingPolicy.FLOW_RULE
                : governedPoolTask ? RoutingPolicy.GOVERNED_POOL : RoutingPolicy.MANUAL_REVIEW;
        boolean sourceFlowPoolFirstTask = ruleResolver.isSourceFlowPoolFirstTask(repaired);
        return new FlowResolution(repaired, policy, flowRuleTask, sourceFlowPoolFirstTask, simulation.flowVersion());
    }

    public boolean isFlowRuleTask(TaskRecord task) {
        return ruleResolver.isFlowRuleTask(task);
    }

    public boolean isSourceFlowPoolFirstTask(TaskRecord task) {
        return ruleResolver.isSourceFlowPoolFirstTask(task);
    }

    public boolean isGovernedPoolTask(TaskRecord task) {
        return ruleResolver.isGovernedPoolTask(task);
    }

    public boolean isAuthoritativePoolTask(TaskRecord task) {
        return ruleResolver.isAuthoritativePoolTask(task);
    }

    public String decisionSuffix(TaskRecord task) {
        return ruleResolver.decisionSuffix(task);
    }

    public java.util.List<String> requiredSkills(TaskRecord task) {
        return ruleResolver.requiredSkills(task);
    }
}
