package com.opensocket.aievent.core.routing.flow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Owns Source Flow / Rule runtime repair and stable routing metadata projection.
 *
 * <p>Phase 3-5 moves the former RoutingDecisionService flow-rule helper logic
 * here without changing routingPath, matchedFlowId, matchedRuleId, targetPoolId,
 * assignedPoolId, requestedSkill, or fallback behavior.</p>
 */
public class RuleResolver {
    private static final Logger log = LoggerFactory.getLogger(RuleResolver.class);

    private final RoutingProperties properties;
    private final FlowRuleRoutingService flowRuleRoutingService;

    public RuleResolver(RoutingProperties properties, FlowRuleRoutingService flowRuleRoutingService) {
        this.properties = properties;
        this.flowRuleRoutingService = flowRuleRoutingService;
    }

    public TaskRecord applyRuntimeRepair(TaskRecord task) {
        // A2A Governance is an upstream routing authority. Once it resolves a governed
        // target Agent Pool, Dispatch must not run Source Flow repair again because that
        // would either overwrite the A2A Policy Pool or fail the Child Task with
        // NO_ACTIVE_FLOW_RULE even though its Pool is already authoritative.
        if (task == null || isFlowRuleTask(task) || isGovernedPoolTask(task)
                || !properties.isFlowRuleRoutingEnabled() || flowRuleRoutingService == null) {
            return task;
        }
        try {
            FlowRuleRoutingPlan plan = flowRuleRoutingService.resolve(task);
            if (plan == null || !plan.isMatched()) {
                log.warn("routing_flow_rule_runtime_repair_not_matched taskId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} reason={}",
                        task.getTaskId(), task.getSourceSystem(), task.getEventStage(), task.getEventType(), task.getObjectType(), task.getErrorCode(),
                        plan == null ? "NO_PLAN" : plan.getReason());
                return task;
            }
            task.setMatchedFlowId(plan.getFlowId());
            task.setMatchedRuleId(plan.getRuleId());
            task.setRequestedSkill(plan.getRequestedSkill());
            task.setEventStage(firstNonBlank(plan.getEventStage(), task.getEventStage(), "EXTERNAL"));
            task.setTargetSystem(firstNonBlank(plan.getTargetSystem(), task.getTargetSystem()));
            task.setHandoffMode(firstNonBlank(plan.getHandoffMode(), task.getHandoffMode(), "DIRECT_ASSIGN"));
            task.setTargetPoolId(firstNonBlank(plan.getTargetPoolId(), task.getTargetPoolId()));
            task.setAssignedPoolId(firstNonBlank(plan.getTargetPoolId(), task.getAssignedPoolId()));
            task.setRoutingPath(firstNonBlank(plan.getRoutingPath(), plan.isSourceDefaultPool() ? "SOURCE_FLOW_DEFAULT_POOL" : "FLOW_RULE"));
            task.setRoutingPolicy("FLOW_RULE");
            task.setRequiredCapabilities(List.of());
            log.info("routing_flow_rule_runtime_repaired taskId={} matchedFlowId={} matchedRuleId={} targetPoolId={} sourceDefaultPool={} eventStage={} routingPath={}",
                    task.getTaskId(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getTargetPoolId(), plan.isSourceDefaultPool(), task.getEventStage(), task.getRoutingPath());
            return task;
        } catch (Exception ex) {
            log.warn("routing_flow_rule_runtime_repair_failed taskId={} reason={}: {}",
                    task.getTaskId(), ex.getClass().getSimpleName(), ex.getMessage());
            return task;
        }
    }


    /** C7 Draft simulation path. It may inspect the explicitly selected Draft Flow but never persists routing evidence. */
    public SimulationRepairResult applySimulationRepair(TaskRecord task, java.util.Map<String,Object> matchAttributes) {
        if (task == null || isFlowRuleTask(task) || isGovernedPoolTask(task)
                || !properties.isFlowRuleRoutingEnabled() || flowRuleRoutingService == null) {
            return new SimulationRepairResult(task, null);
        }
        try {
            FlowRuleRoutingPlan plan = flowRuleRoutingService.resolveForSimulation(task, matchAttributes == null ? java.util.Map.of() : matchAttributes);
            if (plan == null || !plan.isMatched()) {
                return new SimulationRepairResult(task, null);
            }
            task.setMatchedFlowId(plan.getFlowId());
            task.setMatchedRuleId(plan.getRuleId());
            task.setRequestedSkill(plan.getRequestedSkill());
            task.setEventStage(firstNonBlank(plan.getEventStage(), task.getEventStage(), "EXTERNAL"));
            task.setTargetSystem(firstNonBlank(plan.getTargetSystem(), task.getTargetSystem()));
            task.setHandoffMode(firstNonBlank(plan.getHandoffMode(), task.getHandoffMode(), "DIRECT_ASSIGN"));
            task.setTargetPoolId(firstNonBlank(plan.getTargetPoolId(), task.getTargetPoolId()));
            task.setAssignedPoolId(firstNonBlank(plan.getTargetPoolId(), task.getAssignedPoolId()));
            task.setRoutingPath(firstNonBlank(plan.getRoutingPath(), "FLOW_RULE"));
            task.setRoutingPolicy("FLOW_RULE");
            task.setRequiredCapabilities(plan.getRequiredSkills());
            return new SimulationRepairResult(task, plan.getFlowVersion());
        } catch (Exception ex) {
            log.warn("routing_flow_rule_simulation_repair_failed taskId={} reason={}: {}",
                    task.getTaskId(), ex.getClass().getSimpleName(), ex.getMessage());
            return new SimulationRepairResult(task, null);
        }
    }

    public record SimulationRepairResult(TaskRecord task, String flowVersion) {}

    public boolean isFlowRuleTask(TaskRecord task) {
        if (!properties.isFlowRuleRoutingEnabled() || task == null || blank(task.getMatchedFlowId())) {
            return false;
        }
        String path = normalize(task.getRoutingPath());
        boolean standardPath = "FLOW_RULE".equals(path) || "SOURCE_FLOW_DEFAULT_POOL".equals(path) || "SOURCE_FLOW_POOL".equals(path);
        return standardPath && (!blank(task.getMatchedRuleId()) || !blank(task.getTargetPoolId()) || !blank(task.getAssignedPoolId()));
    }

    public boolean isSourceFlowPoolFirstTask(TaskRecord task) {
        if (task == null || !isFlowRuleTask(task)) {
            return false;
        }
        String path = normalize(task.getRoutingPath());
        return !blank(task.getTargetPoolId())
                || !blank(task.getAssignedPoolId())
                || "SOURCE_FLOW_DEFAULT_POOL".equals(path)
                || "SOURCE_FLOW_POOL".equals(path)
                || "SOURCE_DEFAULT".equals(normalize(task.getMatchedRuleId()));
    }


    /**
     * True when an upstream governance authority already resolved the candidate Pool.
     *
     * <p>The first canonical use is A2A Policy -> Agent Pool. This is intentionally
     * stricter than merely checking targetPoolId: an arbitrary Task may not bypass
     * Source Flow governance just because a caller supplied a Pool id.</p>
     */
    public boolean isGovernedPoolTask(TaskRecord task) {
        if (task == null || blank(task.getTargetPoolId())) {
            return false;
        }
        String path = normalize(task.getRoutingPath());
        return "A2A_POLICY_TO_AGENT_POOL".equals(path)
                && !blank(task.getA2aPolicyId());
    }

    /** Pool authority may come from Source Flow or another explicit governance authority. */
    public boolean isAuthoritativePoolTask(TaskRecord task) {
        return isSourceFlowPoolFirstTask(task) || isGovernedPoolTask(task);
    }

    public String decisionSuffix(TaskRecord task) {
        if (!isFlowRuleTask(task)) {
            return "";
        }
        return "; flowRule=FLOW_RULE matchedFlowId=" + task.getMatchedFlowId()
                + ", matchedRuleId=" + task.getMatchedRuleId()
                + ", eventStage=" + display(task.getEventStage())
                + ", targetPoolId=" + display(task.getTargetPoolId())
                + ", assignedPoolId=" + display(task.getAssignedPoolId())
                + ", classificationStatus=" + display(task.getClassificationStatus())
                + ", capabilityTagReference=" + display(task.getRequestedSkill());
    }

    public List<String> requiredSkills(TaskRecord task) {
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        String requestedSkill = normalize(task == null ? null : task.getRequestedSkill());
        if (!blank(requestedSkill)) {
            skills.add(requestedSkill);
        }
        return skills.stream().toList();
    }

    private String firstNonBlank(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value == null) continue;
            String text = value.toString();
            if (!blank(text)) return text;
        }
        return null;
    }

    private String display(String value) {
        return blank(value) ? "-" : value;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
