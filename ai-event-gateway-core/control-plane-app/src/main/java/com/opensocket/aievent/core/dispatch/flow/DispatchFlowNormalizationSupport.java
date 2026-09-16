package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.routing.governance.CapabilityRequirementMode;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;

/**
 * Pure Dispatch Flow normalization support.
 *
 * <p>This helper owns representation normalization only. It is deliberately
 * package-private, has no Spring annotations, performs no persistence, and
 * cannot become a second Dispatch Flow management authority.</p>
 */
final class DispatchFlowNormalizationSupport {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowNormalizationSupport.class);
    private static final Set<String> SUPPORTED_POOL_SELECTION_STRATEGIES =
            Set.of("LOWEST_LOAD", "WEIGHTED_SCORE", "MANUAL_ONLY");

    private DispatchFlowNormalizationSupport() {
    }

    static DispatchFlowView normalizeFlowBase(DispatchFlowView request) {
        DispatchFlowView flow = request == null ? new DispatchFlowView() : request;
        String tenantId = normalizeTenant(flow.getTenantId());
        String source = normalizeCode(requireNonBlank(flow.getSourceSystem(), "sourceSystem"));
        String flowCode = normalizeCode(firstNonBlank(flow.getFlowCode(), source + "_DISPATCH_FLOW"));
        String flowId = firstNonBlank(flow.getFlowId(), "flow-" + flowCode.toLowerCase(Locale.ROOT).replace('_', '-'));
        flow.setTenantId(tenantId);
        flow.setSourceSystem(source);
        flow.setFlowCode(flowCode);
        flow.setFlowId(flowId);
        flow.setFlowName(firstNonBlank(flow.getFlowName(), source + " Dispatch Flow"));
        flow.setStatus(normalizeCode(firstNonBlank(flow.getStatus(), "ACTIVE")));
        flow.setDefaultCapabilityRequirementMode(normalizeCode(firstNonBlank(
                flow.getDefaultCapabilityRequirementMode(), CapabilityRequirementMode.NONE.name())));
        flow.setDefaultRequiredOperation(normalizeCode(firstNonBlank(flow.getDefaultRequiredOperation(), "ANALYZE")));
        flow.setDefaultSideEffectLevel(normalizeCode(firstNonBlank(flow.getDefaultSideEffectLevel(), "NONE")));
        flow.setDefaultCandidatePoolMode(normalizeCode(firstNonBlank(
                flow.getDefaultCandidatePoolMode(), CandidatePoolMode.EXPLICIT_FLOW_AGENTS.name())));
        flow.setDefaultRoutingStrategy(normalizeCode(firstNonBlank(flow.getDefaultRoutingStrategy(), "WEIGHTED_SCORE")));
        flow.setDefaultIssueSyncPolicy(normalizeIssueSyncPolicy(firstNonBlank(flow.getDefaultIssueSyncPolicy(), "OPTIONAL"), false));
        return flow;
    }

    static DispatchFlowRuleView normalizeRule(String tenantId, String flowId, DispatchFlowRuleView request) {
        DispatchFlowRuleView rule = request == null ? new DispatchFlowRuleView() : request;
        String source = normalizeCode(requireNonBlank(rule.getSourceSystem(), "sourceSystem"));
        String eventStage = normalizeCode(firstNonBlank(rule.getEventStage(), "EXTERNAL"));
        String eventType = normalizeWildcard(firstNonBlank(rule.getEventType(), "*"));
        // requestedSkill is no longer an operator-owned dispatch selector.
        // Required Capability rows are synchronized after rule normalization.
        String requestedSkill = null;
        String capabilityRequirementMode = CapabilityRequirementMode.NONE.name();
        String ruleCode = normalizeCode(firstNonBlank(
                rule.getRuleCode(), source + "_" + eventStage + "_" + normalizeWildcard(eventType).replace("*", "ANY") + "_RULE"));
        rule.setTenantId(tenantId);
        rule.setFlowId(flowId);
        rule.setRuleCode(ruleCode);
        rule.setRuleId(firstNonBlank(
                rule.getRuleId(), "rule-" + safeIdPart(tenantId) + "-" + ruleCode.toLowerCase(Locale.ROOT).replace('_', '-')));
        rule.setRuleName(firstNonBlank(rule.getRuleName(), ruleCode.replace('_', ' ')));
        rule.setServiceCode(normalizeNullable(rule.getServiceCode()));
        rule.setRuleScope(normalizeCode(firstNonBlank(
                rule.getRuleScope(), "A2A".equals(eventStage) ? "A2A_DISPATCH" : "EXTERNAL_INTAKE")));
        rule.setEventStage(eventStage);
        rule.setSourceSystem(source);
        rule.setEventType(eventType);
        rule.setObjectType(normalizeWildcard(firstNonBlank(rule.getObjectType(), "*")));
        rule.setErrorCode(normalizeWildcard(firstNonBlank(rule.getErrorCode(), "*")));
        rule.setRequestedSkill(requestedSkill);
        rule.setCapabilityRequirementMode(capabilityRequirementMode);
        rule.setRequiredOperation(normalizeCode(firstNonBlank(rule.getRequiredOperation(), "ANALYZE")));
        rule.setSideEffectLevel(normalizeCode(firstNonBlank(rule.getSideEffectLevel(), "NONE")));
        rule.setCandidatePoolMode(CandidatePoolMode.EXPLICIT_FLOW_AGENTS.name());
        rule.setRoutingStrategy(normalizeCode(firstNonBlank(rule.getRoutingStrategy(), "WEIGHTED_SCORE")));
        rule.setExplicitActionAuthorizationRequired(
                rule.getExplicitActionAuthorizationRequired() == null
                        ? Boolean.TRUE
                        : rule.getExplicitActionAuthorizationRequired());
        rule.setRequirementModelVersion(
                rule.getRequirementModelVersion() == null ? 10 : Math.max(10, rule.getRequirementModelVersion()));
        rule.setIssueSyncPolicy(normalizeIssueSyncPolicy(rule.getIssueSyncPolicy(), true));
        rule.setEnabled(rule.getEnabled() == null ? Boolean.TRUE : rule.getEnabled());
        rule.setLegacyStatus("FLOW_OWNED_READY");
        return rule;
    }

    static DispatchFlowRequiredSkillView normalizeCapability(
            String tenantId, String flowId, DispatchFlowRequiredSkillView request) {
        DispatchFlowRequiredSkillView capability = request == null ? new DispatchFlowRequiredSkillView() : request;
        String eventStage = normalizeCode(firstNonBlank(capability.getEventStage(), "EXTERNAL"));
        String agentRole = normalizeCode(firstNonBlank(capability.getAgentRole(), "LEAD"));
        String capabilityCode = normalizeCapabilityCode(requireNonBlank(capability.getCapabilityCode(), "capabilityCode"));
        capability.setTenantId(tenantId);
        capability.setFlowId(flowId);
        capability.setEventStage(eventStage);
        capability.setAgentRole(agentRole);
        capability.setCapabilityCode(capabilityCode);
        capability.setId(firstNonBlank(
                capability.getId(),
                "capability-" + safeIdPart(flowId) + "-" + eventStage.toLowerCase(Locale.ROOT)
                        + "-" + safeIdPart(capabilityCode)));
        capability.setCapabilityName(firstNonBlank(capability.getCapabilityName(), capabilityCode));
        capability.setCapabilityKind(normalizeCode(firstNonBlank(capability.getCapabilityKind(), "FLOW_CAPABILITY")));
        capability.setRequired(capability.getRequired() == null ? Boolean.TRUE : capability.getRequired());
        capability.setOpenClawCapability(
                capability.getOpenClawCapability() == null ? Boolean.TRUE : capability.getOpenClawCapability());
        capability.setLegacyStatus("FLOW_OWNED_CAPABILITY");
        return capability;
    }

    static DispatchFlowAgentView normalizeAgent(String tenantId, String flowId, DispatchFlowAgentView request) {
        DispatchFlowAgentView agent = request == null ? new DispatchFlowAgentView() : request;
        String eventStage = normalizeCode(firstNonBlank(agent.getEventStage(), "EXTERNAL"));
        String agentRole = normalizeCode(firstNonBlank(agent.getAgentRole(), "LEAD"));
        String agentId = requireNonBlank(agent.getAgentId(), "agentId");
        agent.setTenantId(tenantId);
        agent.setFlowId(flowId);
        agent.setEventStage(eventStage);
        agent.setAgentRole(agentRole);
        agent.setAgentId(agentId);
        agent.setId(firstNonBlank(
                agent.getId(),
                "flow-agent-" + flowId.toLowerCase(Locale.ROOT) + "-" + agentId.toLowerCase(Locale.ROOT).replace('_', '-')));
        agent.setAssignmentStatus(normalizeCode(firstNonBlank(agent.getAssignmentStatus(), "ACTIVE")));
        agent.setRuntimeStatus(normalizeCode(firstNonBlank(agent.getRuntimeStatus(), "UNKNOWN")));
        agent.setApprovalStatus(normalizeCode(firstNonBlank(agent.getApprovalStatus(), "APPROVED")));
        agent.setReadinessStatus(normalizeCode(firstNonBlank(agent.getReadinessStatus(), "READY")));
        agent.setLegacyStatus("FLOW_RULE_AGENT_ASSIGNMENT");
        return agent;
    }

    static String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) return "-";
        return ex.getMessage().replace('\n', ' ').replace('\r', ' ');
    }

    static String normalizeTenant(String value) {
        return requireNonBlank(value, "tenantId").trim();
    }


    static String normalizeIssueSyncPolicy(String value, boolean nullable) {
        if (blank(value)) {
            return nullable ? null : "OPTIONAL";
        }
        String normalized = normalizeCode(value);
        return switch (normalized) {
            case "NONE", "NEVER" -> "NONE";
            case "OPTIONAL", "ON_FAILURE", "FAILURE_ONLY" -> "OPTIONAL";
            case "REQUIRED", "ALWAYS" -> "REQUIRED";
            case "MANUAL" -> "MANUAL";
            default -> throw new IllegalArgumentException("Unsupported Issue behavior: " + value + ". Use NONE, OPTIONAL/ON_FAILURE, REQUIRED/ALWAYS, or MANUAL.");
        };
    }

    static String normalizeSupportedPoolSelectionStrategy(String strategy) {
        String normalized = normalizeCode(firstNonBlank(strategy, "LOWEST_LOAD"));
        if (SUPPORTED_POOL_SELECTION_STRATEGIES.contains(normalized)) {
            return normalized;
        }
        log.warn(
                "unsupported_agent_pool_selection_strategy_normalized rawSelectionStrategy={} normalizedSelectionStrategy=LOWEST_LOAD selectionStrategyContract=SUPPORTED_POOL_STRATEGY",
                strategy);
        return "LOWEST_LOAD";
    }

    static String normalizeCode(String value) {
        if (blank(value)) return null;
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    /** Canonical Capability identity is lower-case dotted semantic notation. Do not apply legacy enum/code normalization. */
    static String normalizeCapabilityCode(String value) {
        if (blank(value)) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeNullable(String value) {
        return blank(value) ? null : normalizeCode(value);
    }

    /** Preserve database identities such as agent_pools.pool_id. */
    static String preserveNullableId(String value) {
        return blank(value) ? null : value.trim();
    }

    static String normalizeWildcard(String value) {
        if (blank(value)) return "*";
        String normalized = normalizeCode(value);
        return "ANY".equals(normalized) ? "*" : normalized;
    }

    static String safeIdPart(String value) {
        String normalized = value == null
                ? "tenant"
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "tenant" : normalized;
    }

    static String requireNonBlank(String value, String fieldName) {
        if (blank(value)) {
            throw new IllegalArgumentException(fieldName + " is required for DB-backed Dispatch Flow CRUD.");
        }
        return value;
    }

    static String emptyToNull(String value) {
        return blank(value) ? null : value;
    }

    static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
