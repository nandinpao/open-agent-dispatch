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
        if (task == null || isFlowRuleTask(task) || !properties.isFlowRuleRoutingEnabled() || flowRuleRoutingService == null) {
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
