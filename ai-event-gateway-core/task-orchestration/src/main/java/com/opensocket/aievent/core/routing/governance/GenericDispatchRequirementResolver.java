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
 * Stage 8 direct-dispatch requirement resolver.
 *
 * The standard runtime contract is owned only by the matched Dispatch Flow:
 * Flow Rule -> Flow Agent Assignment -> optional required Capability.
 * No parallel governance repository, fallback pool, source policy, operation profile,
 * action grant, or source assignment is consulted here.
 */
@Service
public class GenericDispatchRequirementResolver implements DispatchRequirementResolver {
    private static final int RESOLVER_VERSION = 3;

    public GenericDispatchRequirementResolver() {
    }

    @Override
    public DispatchRequirementResolution resolve(TaskRecord task, FlowRuleRoutingPlan plan) {
        if (task == null) throw new IllegalArgumentException("task is required");
        if (plan == null || !plan.isMatched()) {
            return blocked(task, plan, RequirementResolutionMode.NONE,
                    "FLOW_RULE_NOT_MATCHED", List.of());
        }

        List<String> capabilities = explicitCapabilities(plan);
        DispatchRequirementResolution resolution = base(task, plan,
                capabilities.isEmpty() ? RequirementResolutionMode.NONE : RequirementResolutionMode.EXPLICIT_CAPABILITY);
        resolution.setRequiredOperations(List.of());
        resolution.setRequiredCapabilities(capabilities);
        resolution.setOutcome(RequirementDecisionStatus.RESOLVED);
        resolution.setReasonCode(capabilities.isEmpty()
                ? "STANDARD_FLOW_DIRECT_NO_CAPABILITY_REQUIREMENT_RESOLVED"
                : "STANDARD_FLOW_DIRECT_CAPABILITY_REQUIREMENT_RESOLVED");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dispatchAuthority", "DISPATCH_FLOW_DIRECT");
        details.put("candidateAuthority", "FLOW_AGENT_ASSIGNMENT");
        details.put("parallelDispatchModelsRemoved", true);
        details.put("requiredCapabilityCount", capabilities.size());
        resolution.setDetails(details);
        resolution.setRoutingStrategy(parseEnum(
                GenericRoutingStrategy.class, plan.getRoutingStrategy(), GenericRoutingStrategy.WEIGHTED_SCORE));
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
        resolution.setCandidatePoolMode(CandidatePoolMode.EXPLICIT_FLOW_AGENTS);
        resolution.setExplicitActionAuthorizationRequired(false);
        resolution.setResolverVersion(RESOLVER_VERSION);
        return resolution;
    }

    private static List<String> explicitCapabilities(FlowRuleRoutingPlan plan) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (plan != null && plan.getRequiredSkills() != null) {
            plan.getRequiredSkills().stream().map(GenericDispatchRequirementResolver::normalize)
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
