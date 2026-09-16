package com.opensocket.aievent.core.dispatch.flow;

import static com.opensocket.aievent.core.dispatch.flow.DispatchFlowNormalizationSupport.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.routing.governance.CapabilityRequirementMode;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;

/**
 * Normalization and invariant validation for the Dispatch Flow aggregate.
 *
 * <p>This collaborator owns mutation preconditions only. It does not write rows, dispatch tasks,
 * select runtime candidates, or change any A2A authority.</p>
 */
final class DispatchFlowAggregatePolicy {
    private final NamedParameterJdbcTemplate jdbc;
    private final DispatchSourceOwnershipResolver sourceOwnership;

    DispatchFlowAggregatePolicy(NamedParameterJdbcTemplate jdbc, DispatchSourceOwnershipResolver sourceOwnership) {
        this.jdbc = jdbc;
        this.sourceOwnership = sourceOwnership;
    }

    DispatchFlowView normalizeFlowAggregate(DispatchFlowView request) {
        DispatchFlowView flow = normalizeFlow(request);
        List<DispatchFlowRuleView> rules = new ArrayList<>();
        for (DispatchFlowRuleView rawRule : flow.getRules()) {
            DispatchFlowRuleView rule = rawRule == null ? new DispatchFlowRuleView() : rawRule;
            validateChildIdentity(flow, rule.getTenantId(), rule.getFlowId(), "rule");
            if (blank(rule.getSourceSystem())) rule.setSourceSystem(flow.getSourceSystem());
            DispatchFlowRuleView normalizedRule = normalizeRule(flow.getTenantId(), flow.getFlowId(), rule);
            // Current standard setting UI routes to Agent Pool / Work Queue first.
            normalizedRule.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL.name());
            rules.add(normalizedRule);
        }
        flow.setRules(rules);

        List<DispatchFlowRequiredSkillView> capabilities = new ArrayList<>();
        for (DispatchFlowRequiredSkillView rawCapability : flow.getRequiredSkills()) {
            DispatchFlowRequiredSkillView capability = rawCapability == null ? new DispatchFlowRequiredSkillView() : rawCapability;
            validateChildIdentity(flow, capability.getTenantId(), capability.getFlowId(), "requiredCapability");
            capabilities.add(normalizeCapability(flow.getTenantId(), flow.getFlowId(), capability));
        }
        flow.setRequiredSkills(capabilities);

        List<DispatchFlowAgentView> agents = new ArrayList<>();
        for (DispatchFlowAgentView rawAgent : flow.getAgents()) {
            DispatchFlowAgentView agent = rawAgent == null ? new DispatchFlowAgentView() : rawAgent;
            validateChildIdentity(flow, agent.getTenantId(), agent.getFlowId(), "agentSelection");
            agents.add(normalizeAgent(flow.getTenantId(), flow.getFlowId(), agent));
        }
        flow.setAgents(agents);
        flow.setDefaultCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL.name());
        if (metadataFlag(flow, "legacyChildrenPreserved")) {
            suppressLegacyAgentGates(flow);
            suppressLegacyCapabilityGates(flow);
        } else {
            synchronizeAgentSelections(flow);
            synchronizeFlowRequiredCapabilities(flow);
        }
        flow.setDefaultCapabilityRequirementMode(flow.getRules().stream()
                .anyMatch(rule -> CapabilityRequirementMode.EXPLICIT.name().equals(rule.getCapabilityRequirementMode()))
                ? CapabilityRequirementMode.EXPLICIT.name()
                : CapabilityRequirementMode.NONE.name());
        return flow;
    }

    void validateAggregate(DispatchFlowView flow) {
        requireUnique(flow.getRules().stream().map(DispatchFlowRuleView::getRuleId).toList(), "ruleId");
        requireUnique(flow.getRules().stream().map(DispatchFlowRuleView::getRuleCode).toList(), "ruleCode");
        requireUnique(flow.getRequiredSkills().stream().map(DispatchFlowRequiredSkillView::getId).toList(), "requiredCapabilityId");
        requireUnique(flow.getRequiredSkills().stream()
                .map(capability -> firstNonBlank(capability.getRuleId(), "*") + "|" + capability.getEventStage() + "|" + capability.getAgentRole() + "|" + capability.getSkillCode())
                .toList(), "requiredCapabilityIdentity");
        requireUnique(flow.getAgents().stream().map(DispatchFlowAgentView::getId).toList(), "flowAgentAssignmentId");
        requireUnique(flow.getAgents().stream()
                .map(agent -> agent.getAgentId() + "|" + agent.getEventStage() + "|" + agent.getAgentRole())
                .toList(), "flowAgentSelectionIdentity");

        Set<String> ruleIds = new LinkedHashSet<>(flow.getRules().stream().map(DispatchFlowRuleView::getRuleId).toList());
        for (DispatchFlowRuleView rule : flow.getRules()) {
            if (!flow.getSourceSystem().equals(rule.getSourceSystem())) {
                throw new IllegalArgumentException("Flow Rule sourceSystem must match its parent Dispatch Flow.");
            }
        }
        validateSamePriorityRuleAmbiguity(flow);
        for (DispatchFlowRequiredSkillView capability : flow.getRequiredSkills()) {
            if (!blank(capability.getRuleId()) && !ruleIds.contains(capability.getRuleId())) {
                throw new IllegalArgumentException("Required Capability references a Rule outside this Dispatch Flow: " + capability.getRuleId());
            }
        }
        validateChildRowOwnership(flow);

        if (isActive(flow.getStatus())) {
            boolean hasEnabledRule = flow.getRules().stream().anyMatch(rule -> Boolean.TRUE.equals(rule.getEnabled()));
            boolean hasDefaultPool = !blank(flow.getDefaultPoolId());
            boolean hasRulePool = flow.getRules().stream().anyMatch(rule -> !blank(rule.getTargetPoolId()));
            boolean hasLegacyAgentSelection = flow.getAgents().stream().anyMatch(this::isActiveApprovedAgentSelection);
            if (!hasEnabledRule && !hasDefaultPool) {
                throw new IllegalArgumentException("An ACTIVE Source Flow requires a default Agent Pool or at least one enabled Pool override Rule.");
            }
            if (!hasDefaultPool && !hasRulePool && !hasLegacyAgentSelection) {
                throw new IllegalArgumentException("An ACTIVE Source Flow requires a default Agent Pool, a Rule target Pool, or a legacy approved Agent selection.");
            }
        }

        validateAgentPoolReferences(flow);
        validateExplicitCapabilities(flow);
    }

    private void validateSamePriorityRuleAmbiguity(DispatchFlowView flow) {
        List<DispatchFlowRuleView> active = flow.getRules().stream().filter(rule -> Boolean.TRUE.equals(rule.getEnabled())).toList();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                DispatchFlowRuleView left = active.get(i), right = active.get(j);
                if (!java.util.Objects.equals(left.getPriority(), right.getPriority())) continue;
                if (overlaps(left.getSourceSystem(), right.getSourceSystem())
                        && overlaps(left.getOriginSourceSystem(), right.getOriginSourceSystem())
                        && overlaps(left.getTargetSystem(), right.getTargetSystem())
                        && overlaps(left.getEventStage(), right.getEventStage())
                        && overlaps(left.getEventType(), right.getEventType())
                        && overlaps(left.getObjectType(), right.getObjectType())
                        && overlaps(left.getErrorCode(), right.getErrorCode())) {
                    throw new IllegalArgumentException("FLOW_RULE_SAME_PRIORITY_AMBIGUOUS: rules " + left.getRuleId() + " and " + right.getRuleId()
                            + " overlap at priority " + left.getPriority() + ". Change priority or make deterministic criteria mutually exclusive.");
                }
            }
        }
    }

    private boolean overlaps(String left, String right) {
        String a = normalizeWildcard(firstNonBlank(left, "*"));
        String b = normalizeWildcard(firstNonBlank(right, "*"));
        return "*".equals(a) || "*".equals(b) || a.equals(b);
    }

    private void validateChildRowOwnership(DispatchFlowView flow) {
        validateRuleIdOwnership(flow);
        validateScopedChildIdOwnership(
                flow,
                "flow_required_capabilities",
                flow.getRequiredSkills().stream().map(DispatchFlowRequiredSkillView::getId).toList(),
                "Required Capability");
        validateScopedChildIdOwnership(
                flow,
                "flow_agent_assignments",
                flow.getAgents().stream().map(DispatchFlowAgentView::getId).toList(),
                "Flow Agent selection");
    }

    private void validateRuleIdOwnership(DispatchFlowView flow) {
        if (flow.getRules().isEmpty()) return;
        List<String> ruleIds = flow.getRules().stream().map(DispatchFlowRuleView::getRuleId).toList();
        List<Map<String, Object>> existing = jdbc.queryForList("""
                select policy_id, tenant_id, flow_id
                  from dispatch_policies
                 where policy_id in (:ruleIds)
                """, new MapSqlParameterSource().addValue("ruleIds", ruleIds));
        for (Map<String, Object> row : existing) {
            String existingTenant = String.valueOf(row.get("tenant_id"));
            String existingFlowId = row.get("flow_id") == null ? null : String.valueOf(row.get("flow_id"));
            if (!flow.getTenantId().equals(existingTenant)) {
                throw new IllegalArgumentException("Flow Rule ID is already owned by another tenant: " + row.get("policy_id"));
            }
            if (!flow.getFlowId().equals(existingFlowId)) {
                throw new IllegalArgumentException("Flow Rule ID is already owned by another Dispatch Flow: " + row.get("policy_id"));
            }
        }
    }

    private void validateScopedChildIdOwnership(
            DispatchFlowView flow,
            String table,
            List<String> childIds,
            String childType) {
        if (childIds.isEmpty()) return;
        List<Map<String, Object>> existing = jdbc.queryForList(
                "select id, flow_id from " + table + " where tenant_id = :tenantId and id in (:childIds)",
                new MapSqlParameterSource()
                        .addValue("tenantId", flow.getTenantId())
                        .addValue("childIds", childIds));
        for (Map<String, Object> row : existing) {
            String existingFlowId = row.get("flow_id") == null ? null : String.valueOf(row.get("flow_id"));
            if (!flow.getFlowId().equals(existingFlowId)) {
                throw new IllegalArgumentException(childType + " ID is already owned by another Dispatch Flow: " + row.get("id"));
            }
        }
    }

    private void synchronizeAgentSelections(DispatchFlowView flow) {
        if (flow.getAgents().isEmpty()) return;
        List<String> agentIds = flow.getAgents().stream().map(DispatchFlowAgentView::getAgentId).distinct().toList();
        Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
        jdbc.query("""
                select agent_id, agent_name, approval_status, enabled
                  from agent_profiles
                 where tenant_id = :tenantId
                   and agent_id in (:agentIds)
                """, new MapSqlParameterSource()
                        .addValue("tenantId", flow.getTenantId())
                        .addValue("agentIds", agentIds), rs -> {
                    Map<String, Object> profile = new LinkedHashMap<>();
                    profile.put("agentName", rs.getString("agent_name"));
                    profile.put("approvalStatus", rs.getString("approval_status"));
                    profile.put("enabled", rs.getBoolean("enabled"));
                    profiles.put(rs.getString("agent_id"), profile);
                });
        for (DispatchFlowAgentView agent : flow.getAgents()) {
            Map<String, Object> profile = profiles.get(agent.getAgentId());
            if (profile == null) {
                throw new IllegalArgumentException("Agent does not exist in the selected tenant: " + agent.getAgentId());
            }
            String approvalStatus = normalizeCode((String) profile.get("approvalStatus"));
            boolean enabled = Boolean.TRUE.equals(profile.get("enabled"));
            boolean approved = enabled && ("APPROVED".equals(approvalStatus) || "ACTIVE".equals(approvalStatus));
            agent.setAgentName(firstNonBlank((String) profile.get("agentName"), agent.getAgentName(), agent.getAgentId()));
            agent.setApprovalStatus(approved ? "APPROVED" : approvalStatus);
            agent.setAssignmentStatus(isActive(flow.getStatus()) && approved ? "ACTIVE" : "DRAFT");
            agent.setRuntimeStatus("UNKNOWN");
            agent.setReadinessStatus("NOT_EVALUATED");
            if (isActive(flow.getStatus()) && !approved) {
                throw new IllegalArgumentException("ACTIVE Dispatch Flow Agent must be enabled and approved: " + agent.getAgentId());
            }
        }
    }

    private void suppressLegacyAgentGates(DispatchFlowView flow) {
        for (DispatchFlowAgentView agent : flow.getAgents()) {
            if (blank(agent.getAssignmentStatus())) agent.setAssignmentStatus("LEGACY_REFERENCE");
            if (blank(agent.getRuntimeStatus())) agent.setRuntimeStatus("REFERENCE_ONLY");
            if (blank(agent.getReadinessStatus())) agent.setReadinessStatus("NOT_EVALUATED");
        }
    }

    private void suppressLegacyCapabilityGates(DispatchFlowView flow) {
        for (DispatchFlowRuleView rule : flow.getRules()) {
            rule.setCapabilityRequirementMode(CapabilityRequirementMode.NONE.name());
            rule.setRequestedSkill(null);
        }
    }

    private void synchronizeFlowRequiredCapabilities(DispatchFlowView flow) {
        Map<String, List<DispatchFlowRequiredSkillView>> capabilitiesByRule = new LinkedHashMap<>();
        for (DispatchFlowRequiredSkillView capability : flow.getRequiredSkills()) {
            String key = firstNonBlank(capability.getRuleId(), "*");
            capabilitiesByRule.computeIfAbsent(key, ignored -> new ArrayList<>()).add(capability);
        }
        for (DispatchFlowRuleView rule : flow.getRules()) {
            List<DispatchFlowRequiredSkillView> linked = new ArrayList<>();
            linked.addAll(capabilitiesByRule.getOrDefault(rule.getRuleId(), List.of()));
            linked.addAll(capabilitiesByRule.getOrDefault("*", List.of()).stream()
                    .filter(capability -> rule.getEventStage().equals(capability.getEventStage()))
                    .toList());
            List<String> requiredCodes = linked.stream()
                    .filter(capability -> !Boolean.FALSE.equals(capability.getRequired()))
                    .map(DispatchFlowRequiredSkillView::getCapabilityCode)
                    .distinct()
                    .toList();

            boolean hasAuthoritativeCapabilityRows = !requiredCodes.isEmpty();
            if (hasAuthoritativeCapabilityRows) {
                rule.setCapabilityRequirementMode(CapabilityRequirementMode.EXPLICIT.name());
                // Compatibility projection only: operators cannot set requestedSkill.
                // Standard eligibility reads Required Capabilities exclusively from flow_required_capabilities.
                rule.setRequestedSkill(requiredCodes.get(0));
            } else {
                rule.setCapabilityRequirementMode(CapabilityRequirementMode.NONE.name());
                rule.setRequestedSkill(null);
            }
        }
    }

    private void validateAgentPoolReferences(DispatchFlowView flow) {
        Set<String> poolIds = new LinkedHashSet<>();
        if (!blank(flow.getDefaultPoolId())) poolIds.add(flow.getDefaultPoolId());
        for (DispatchFlowRuleView rule : flow.getRules()) {
            if (!blank(rule.getTargetPoolId())) poolIds.add(rule.getTargetPoolId());
        }
        if (poolIds.isEmpty()) return;
        List<String> available = jdbc.queryForList("""
                select pool_id
                  from agent_pools
                 where tenant_id = :tenantId
                   and pool_id in (:poolIds)
                   and upper(coalesce(status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                """, new MapSqlParameterSource()
                .addValue("tenantId", flow.getTenantId())
                .addValue("poolIds", poolIds), String.class);
        Set<String> missing = new LinkedHashSet<>(poolIds);
        missing.removeAll(available);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Source Flow references inactive or missing Agent Pools: " + String.join(", ", missing));
        }
    }

    private void validateExplicitCapabilities(DispatchFlowView flow) {
        Set<String> capabilityCodes = new LinkedHashSet<>();
        Set<String> explicitRuleIds = new LinkedHashSet<>();
        for (DispatchFlowRuleView rule : flow.getRules()) {
            if (CapabilityRequirementMode.EXPLICIT.name().equals(rule.getCapabilityRequirementMode())) {
                explicitRuleIds.add(rule.getRuleId());
            }
        }
        for (DispatchFlowRequiredSkillView capability : flow.getRequiredSkills()) {
            if (!Boolean.FALSE.equals(capability.getRequired())
                    && (blank(capability.getRuleId()) || explicitRuleIds.contains(capability.getRuleId()))) {
                capabilityCodes.add(capability.getSkillCode());
            }
        }
        if (capabilityCodes.isEmpty()) return;
        List<String> available = jdbc.queryForList("""
                select upper(capability_code)
                  from capability_definitions
                 where tenant_id = :tenantId
                   and upper(capability_code) in (:capabilityCodes)
                   and upper(status) = 'ACTIVE'
                """, new MapSqlParameterSource()
                        .addValue("tenantId", flow.getTenantId())
                        .addValue("capabilityCodes", capabilityCodes), String.class);
        Set<String> missing = new LinkedHashSet<>(capabilityCodes);
        missing.removeAll(available.stream().map(DispatchFlowNormalizationSupport::normalizeCode).toList());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Required Capabilities are not ACTIVE in this tenant's Canonical Capability Catalog: " + String.join(", ", missing));
        }
    }

    private void validateChildIdentity(DispatchFlowView flow, String childTenantId, String childFlowId, String childType) {
        if (!blank(childTenantId) && !flow.getTenantId().equals(childTenantId.trim())) {
            throw new IllegalArgumentException(childType + " tenantId does not match the parent Dispatch Flow.");
        }
        if (!blank(childFlowId) && !flow.getFlowId().equals(childFlowId)) {
            throw new IllegalArgumentException(childType + " flowId does not match the parent Dispatch Flow.");
        }
    }

    private void requireUnique(List<String> values, String fieldName) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (!unique.add(value)) throw new IllegalArgumentException("Duplicate " + fieldName + " in Dispatch Flow aggregate: " + value);
        }
    }

    private boolean isActiveApprovedAgentSelection(DispatchFlowAgentView agent) {
        String assignmentStatus = normalizeCode(agent.getAssignmentStatus());
        String approvalStatus = normalizeCode(agent.getApprovalStatus());
        return ("ACTIVE".equals(assignmentStatus) || "ENABLED".equals(assignmentStatus))
                && ("APPROVED".equals(approvalStatus) || "ACTIVE".equals(approvalStatus));
    }

    private static boolean isActive(String status) {
        String normalized = normalizeCode(status);
        return "ACTIVE".equals(normalized) || "ENABLED".equals(normalized);
    }

    private DispatchFlowView normalizeFlow(DispatchFlowView request) {
        DispatchFlowView flow = DispatchFlowNormalizationSupport.normalizeFlowBase(request);
        sourceOwnership.applyToFlow(flow.getTenantId(), flow.getSourceSystem(), flow);
        return flow;
    }

    private boolean metadataFlag(DispatchFlowView flow, String key) {
        if (flow == null || flow.getMetadata() == null || key == null) return false;
        Object value = flow.getMetadata().get(key);
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }
}
