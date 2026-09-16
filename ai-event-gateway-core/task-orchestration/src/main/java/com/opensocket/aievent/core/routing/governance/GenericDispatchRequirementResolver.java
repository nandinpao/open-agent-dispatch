package com.opensocket.aievent.core.routing.governance;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Canonical Source Flow dispatch requirement resolver.
 *
 * <p>V38-7A1 establishes one authority chain: Source Flow selects an Agent Pool,
 * Agent Pool membership defines the candidate boundary, Task requiredCapabilities
 * are blocking eligibility requirements, and only Core-approved Agent Capability
 * assignments satisfy those requirements. Runtime-reported capabilities are
 * diagnostic observations and never grant qualification.</p>
 */
@Service
public class GenericDispatchRequirementResolver implements DispatchRequirementResolver {
    private static final int RESOLVER_VERSION = 4;

    public GenericDispatchRequirementResolver() {
    }

    @Override
    public DispatchRequirementResolution resolve(TaskRecord task, FlowRuleRoutingPlan plan) {
        if (task == null) throw new IllegalArgumentException("task is required");
        if (plan == null || !plan.isMatched()) {
            return blocked(task, plan, RequirementResolutionMode.NONE,
                    "FLOW_RULE_NOT_MATCHED", List.of());
        }

        String targetPoolId = firstNonBlank(plan.getTargetPoolId(), plan.getDefaultPoolId(),
                task.getTargetPoolId(), task.getAssignedPoolId());
        if (targetPoolId == null || targetPoolId.isBlank()) {
            return blocked(task, plan, RequirementResolutionMode.NONE,
                    "SOURCE_FLOW_HAS_NO_TARGET_POOL", explicitCapabilities(plan, task));
        }

        List<String> capabilities = explicitCapabilities(plan, task);
        DispatchRequirementResolution resolution = base(task, plan,
                capabilities.isEmpty() ? RequirementResolutionMode.NONE : RequirementResolutionMode.EXPLICIT_CAPABILITY);
        resolution.setRequiredOperations(List.of());
        resolution.setRequiredCapabilities(capabilities);
        resolution.setOutcome(RequirementDecisionStatus.RESOLVED);
        resolution.setReasonCode(capabilities.isEmpty()
                ? "SOURCE_FLOW_POOL_NO_CAPABILITY_REQUIREMENT_RESOLVED"
                : "SOURCE_FLOW_POOL_CAPABILITY_REQUIREMENT_RESOLVED");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dispatchAuthority", "DISPATCH_DECISION_ENGINE");
        details.put("candidateAuthority", "AGENT_POOL_MEMBERSHIP");
        details.put("targetPoolId", targetPoolId);
        details.put("targetPoolCode", plan.getTargetPoolCode());
        details.put("selectionStrategy", firstNonBlank(plan.getSelectionStrategy(), "LOWEST_LOAD"));
        details.put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        details.put("runtimeReportedCapabilitiesAuthority", false);
        details.put("parallelDispatchModelsRemoved", true);
        details.put("requiredCapabilityCount", capabilities.size());
        details.put("routingSequence", "FLOW_POOL->REQUIRED_CAPABILITY->RUNTIME_ELIGIBILITY->ROUTING_SCORE");
        resolution.setDetails(details);
        resolution.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL);
        resolution.setRoutingStrategy(routingStrategy(plan));
        resolution.validate();
        return resolution;
    }

    private DispatchRequirementResolution blocked(
            TaskRecord task,
            FlowRuleRoutingPlan plan,
            RequirementResolutionMode mode,
            String reasonCode,
            List<String> capabilities) {
        DispatchRequirementResolution resolution = base(task, plan, mode);
        resolution.setOutcome(RequirementDecisionStatus.BLOCKED);
        resolution.setReasonCode(reasonCode);
        resolution.setRequiredOperations(List.of());
        resolution.setRequiredCapabilities(capabilities);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dispatchAuthority", "DISPATCH_DECISION_ENGINE");
        details.put("candidateAuthority", "AGENT_POOL_MEMBERSHIP");
        details.put("targetPoolId", plan == null ? null : firstNonBlank(plan.getTargetPoolId(), plan.getDefaultPoolId(),
                task == null ? null : task.getTargetPoolId(), task == null ? null : task.getAssignedPoolId()));
        details.put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        details.put("runtimeReportedCapabilitiesAuthority", false);
        resolution.setDetails(details);
        resolution.validate();
        return resolution;
    }

    private DispatchRequirementResolution base(
            TaskRecord task,
            FlowRuleRoutingPlan plan,
            RequirementResolutionMode mode) {
        DispatchRequirementResolution resolution = new DispatchRequirementResolution();
        resolution.setTenantId(require(task.getTenantId(), "tenantId"));
        resolution.setTaskId(require(task.getTaskId(), "taskId"));
        resolution.setMatchedFlowId(plan == null ? null : plan.getFlowId());
        resolution.setMatchedRuleId(plan == null ? null : plan.getRuleId());
        resolution.setSourceSystem(normalizedSource(task));
        resolution.setResolutionMode(mode);
        resolution.setSideEffectLevel(SideEffectLevel.NONE);
        resolution.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL);
        resolution.setExplicitActionAuthorizationRequired(false);
        resolution.setResolverVersion(RESOLVER_VERSION);
        return resolution;
    }

    private static GenericRoutingStrategy routingStrategy(FlowRuleRoutingPlan plan) {
        String selection = normalize(plan == null ? null : plan.getSelectionStrategy());
        if ("MANUAL_ONLY".equals(selection)) return GenericRoutingStrategy.MANUAL_REVIEW;
        if ("LOWEST_LOAD".equals(selection)) return GenericRoutingStrategy.LOWEST_LOAD;
        if ("WEIGHTED_SCORE".equals(selection)) return GenericRoutingStrategy.WEIGHTED_SCORE;
        return parseEnum(GenericRoutingStrategy.class, plan == null ? null : plan.getRoutingStrategy(),
                GenericRoutingStrategy.WEIGHTED_SCORE);
    }

    private static List<String> explicitCapabilities(FlowRuleRoutingPlan plan, TaskRecord task) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (plan != null && plan.getRequiredSkills() != null) {
            plan.getRequiredSkills().stream().map(GenericDispatchRequirementResolver::normalize)
                    .filter(value -> value != null && !value.isBlank()).forEach(values::add);
        }
        // Draft simulation and retry tasks already carry Flow-resolved capability
        // evidence on the Task. Preserve it when an ACTIVE-only persisted lookup
        // is intentionally unavailable.
        if (task != null && task.getRequiredCapabilities() != null) {
            task.getRequiredCapabilities().stream().map(GenericDispatchRequirementResolver::normalize)
                    .filter(value -> value != null && !value.isBlank()).forEach(values::add);
        }
        return values.stream().toList();
    }

    private static String normalizedSource(TaskRecord task) {
        String source = normalize(firstNonBlank(task.getSourceSystem(), task.getOriginSourceSystem()));
        return require(source, "sourceSystem");
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, normalize(value));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
