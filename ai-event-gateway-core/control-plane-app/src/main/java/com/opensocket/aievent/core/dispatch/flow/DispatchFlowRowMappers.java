package com.opensocket.aievent.core.dispatch.flow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;

/**
 * DB row-to-view mapping for Dispatch Flow administration.
 *
 * <p>The mapping implementation is isolated from the aggregate authority so
 * query shape changes do not expand the command orchestration service.</p>
 */
final class DispatchFlowRowMappers {
    private final DispatchFlowJsonCodec json;

    DispatchFlowRowMappers(DispatchFlowJsonCodec json) {
        this.json = json;
    }

    RowMapper<AgentPoolView> agentPool() {
        return (rs, rowNum) -> {
            AgentPoolView pool = new AgentPoolView();
            pool.setTenantId(rs.getString("tenant_id"));
            pool.setPoolId(rs.getString("pool_id"));
            pool.setPoolCode(rs.getString("pool_code"));
            pool.setPoolName(rs.getString("pool_name"));
            pool.setSourceSystem(rs.getString("source_system"));
            pool.setOwnerDepartmentId(optionalString(rs, "owner_department_id"));
            pool.setOwnerGroupId(optionalString(rs, "owner_group_id"));
            pool.setPoolType(rs.getString("pool_type"));
            pool.setSelectionStrategy(rs.getString("selection_strategy"));
            pool.setStatus(rs.getString("status"));
            pool.setDescription(rs.getString("description"));
            pool.setMemberCount(rs.getInt("member_count"));
            pool.setAvailableAgentCount(rs.getInt("available_agent_count"));
            pool.setMetadata(json.readMap(rs.getString("metadata_json")));
            pool.setVersion(rs.getInt("version"));
            pool.setUpdatedBy(rs.getString("updated_by"));
            pool.setUpdatedAt(offset(rs, "updated_at"));
            return pool;
        };
    }

    RowMapper<AgentPoolMemberView> agentPoolMember() {
        return (rs, rowNum) -> {
            AgentPoolMemberView member = new AgentPoolMemberView();
            member.setTenantId(rs.getString("tenant_id"));
            member.setPoolId(rs.getString("pool_id"));
            member.setPoolCode(rs.getString("pool_code"));
            member.setAgentId(rs.getString("agent_id"));
            member.setAgentName(rs.getString("agent_name"));
            member.setMemberStatus(rs.getString("member_status"));
            member.setPriority(rs.getInt("priority"));
            member.setWeight(rs.getInt("weight"));
            member.setApprovalStatus(rs.getString("approval_status"));
            member.setRuntimeStatus(rs.getString("runtime_status"));
            member.setMetadata(json.readMap(rs.getString("metadata_json")));
            member.setVersion(rs.getInt("version"));
            member.setUpdatedBy(rs.getString("updated_by"));
            member.setUpdatedAt(offset(rs, "updated_at"));
            return member;
        };
    }

    RowMapper<DispatchFlowAgentOptionView> agentOption() {
        return (rs, rowNum) -> {
            DispatchFlowAgentOptionView option = new DispatchFlowAgentOptionView();
            option.setTenantId(rs.getString("tenant_id"));
            option.setAgentId(rs.getString("agent_id"));
            option.setAgentName(rs.getString("agent_name"));
            option.setApprovalStatus(rs.getString("approval_status"));
            option.setEnabled(rs.getBoolean("enabled"));
            option.setRiskStatus(rs.getString("risk_status"));
            option.setRuntimeStatus(rs.getString("runtime_status"));
            option.setRuntimeConnected(rs.getBoolean("runtime_connected"));
            option.setHeartbeatHealthy(rs.getBoolean("heartbeat_healthy"));
            option.setCapacityAvailable(rs.getBoolean("capacity_available"));
            option.setActiveFlowCount(rs.getInt("active_flow_count"));
            boolean enabled = Boolean.TRUE.equals(option.getEnabled());
            boolean approved = "APPROVED".equalsIgnoreCase(firstNonBlank(option.getApprovalStatus(), ""));
            boolean runtimeConnected = Boolean.TRUE.equals(option.getRuntimeConnected());
            boolean heartbeatHealthy = Boolean.TRUE.equals(option.getHeartbeatHealthy());
            boolean capacityAvailable = Boolean.TRUE.equals(option.getCapacityAvailable());
            option.setSelectable(enabled && approved && runtimeConnected && heartbeatHealthy && capacityAvailable);
            if (!Boolean.TRUE.equals(option.getSelectable())) {
                if (!enabled) option.setDisabledReason("Agent profile is disabled.");
                else if (!approved) option.setDisabledReason("Agent profile is not approved.");
                else if (!runtimeConnected) option.setDisabledReason("Agent runtime is not connected.");
                else if (!heartbeatHealthy) option.setDisabledReason("Agent heartbeat is stale.");
                else if (!capacityAvailable) option.setDisabledReason("Agent has no available capacity.");
                else option.setDisabledReason("Agent is not selectable for this Dispatch Flow.");
            }
            return option;
        };
    }

    RowMapper<DispatchFlowView> flow() {
        return (rs, rowNum) -> {
            DispatchFlowView flow = new DispatchFlowView();
            flow.setTenantId(rs.getString("tenant_id"));
            flow.setFlowId(rs.getString("flow_id"));
            flow.setFlowCode(rs.getString("flow_code"));
            flow.setFlowName(rs.getString("flow_name"));
            flow.setSourceSystem(rs.getString("source_system"));
            flow.setOwnerDepartmentId(optionalString(rs, "owner_department_id"));
            flow.setOwnerGroupId(optionalString(rs, "owner_group_id"));
            flow.setFlowType(rs.getString("flow_type"));
            flow.setDefaultPoolId(rs.getString("default_pool_id"));
            flow.setStatus(rs.getString("status"));
            flow.setDescription(rs.getString("description"));
            flow.setDefaultCapabilityRequirementMode(rs.getString("default_capability_requirement_mode"));
            flow.setDefaultRequiredOperation(rs.getString("default_required_operation"));
            flow.setDefaultSideEffectLevel(rs.getString("default_side_effect_level"));
            flow.setDefaultCandidatePoolMode(rs.getString("default_candidate_pool_mode"));
            flow.setDefaultRoutingStrategy(rs.getString("default_routing_strategy"));
            flow.setDefaultIssueSyncPolicy(optionalString(rs, "issue_sync_policy"));
            flow.setExternalRuleCount(rs.getInt("external_rule_count"));
            flow.setA2aRuleCount(rs.getInt("a2a_rule_count"));
            flow.setCapabilityCount(rs.getInt("capability_count"));
            flow.setAgentCount(rs.getInt("agent_count"));
            flow.setLastTestStatus("NOT_RUN");
            flow.setMetadata(json.readMap(rs.getString("metadata_json")));
            flow.setVersion(rs.getInt("version"));
            flow.setUpdatedBy(rs.getString("updated_by"));
            flow.setUpdatedAt(offset(rs, "updated_at"));
            return flow;
        };
    }

    RowMapper<DispatchFlowRuleView> rule() {
        return (rs, rowNum) -> {
            DispatchFlowRuleView rule = new DispatchFlowRuleView();
            rule.setTenantId(rs.getString("tenant_id"));
            rule.setRuleId(rs.getString("policy_id"));
            rule.setFlowId(rs.getString("flow_id"));
            rule.setRuleCode(rs.getString("policy_code"));
            rule.setRuleName(rs.getString("policy_name"));
            rule.setServiceCode(rs.getString("service_code"));
            rule.setRuleScope(rs.getString("rule_scope"));
            rule.setEventStage(rs.getString("event_stage"));
            rule.setSourceSystem(rs.getString("source_system"));
            rule.setOriginSourceSystem(rs.getString("origin_source_system"));
            rule.setTargetSystem(rs.getString("target_system"));
            rule.setEventType(rs.getString("event_type"));
            rule.setObjectType(rs.getString("object_type"));
            rule.setErrorCode(rs.getString("error_code"));
            rule.setCondition(json.readMap(rs.getString("condition_json")));
            rule.setPriority(rs.getInt("priority"));
            rule.setMatchMode(rs.getString("match_mode"));
            rule.setTargetPoolId(rs.getString("target_pool_id"));
            rule.setTargetPoolCode(rs.getString("target_pool_code"));
            rule.setRequestedSkill(rs.getString("requested_skill"));
            rule.setCapabilityRequirementMode(rs.getString("capability_requirement_mode"));
            rule.setRequiredOperation(rs.getString("required_operation"));
            rule.setSideEffectLevel(rs.getString("side_effect_level"));
            rule.setCandidatePoolMode(rs.getString("candidate_pool_mode"));
            rule.setRoutingStrategy(rs.getString("routing_strategy"));
            rule.setExplicitActionAuthorizationRequired(rs.getBoolean("explicit_action_authorization_required"));
            rule.setRequirementModelVersion(rs.getInt("requirement_model_version"));
            rule.setHandoffMode(rs.getString("handoff_mode"));
            rule.setIssuePolicyId(rs.getString("issue_policy_id"));
            rule.setIssueSyncPolicy(optionalString(rs, "issue_sync_policy"));
            rule.setEnabled(List.of("ACTIVE", "ENABLED").contains(firstNonBlank(rs.getString("status"), "").toUpperCase(Locale.ROOT)));
            Map<String, Object> metadata = json.readMap(rs.getString("metadata_json"));
            Object priority = metadata.get("priority");
            if (priority instanceof Number number) rule.setPriority(number.intValue());
            rule.setUpdatedAt(offset(rs, "updated_at"));
            return rule;
        };
    }

    RowMapper<DispatchFlowRequiredSkillView> capability() {
        return (rs, rowNum) -> {
            DispatchFlowRequiredSkillView capability = new DispatchFlowRequiredSkillView();
            capability.setTenantId(rs.getString("tenant_id"));
            capability.setId(rs.getString("id"));
            capability.setFlowId(rs.getString("flow_id"));
            capability.setRuleId(rs.getString("rule_id"));
            capability.setEventStage(rs.getString("event_stage"));
            capability.setAgentRole(rs.getString("agent_role"));
            capability.setSkillCode(rs.getString("skill_code"));
            capability.setSkillName(rs.getString("skill_name"));
            capability.setSkillKind(rs.getString("skill_kind"));
            capability.setAuthorityCode(rs.getString("authority_code"));
            capability.setRequired(rs.getBoolean("required"));
            capability.setOpenClawCapability(rs.getBoolean("openclaw_skill"));
            capability.setDescription(rs.getString("description"));
            return capability;
        };
    }

    RowMapper<DispatchFlowAgentView> agent() {
        return (rs, rowNum) -> {
            DispatchFlowAgentView agent = new DispatchFlowAgentView();
            agent.setTenantId(rs.getString("tenant_id"));
            agent.setId(rs.getString("id"));
            agent.setFlowId(rs.getString("flow_id"));
            agent.setAgentId(rs.getString("agent_id"));
            agent.setAgentName(rs.getString("agent_name"));
            agent.setEventStage(rs.getString("event_stage"));
            agent.setAgentRole(rs.getString("agent_role"));
            agent.setAssignmentStatus(rs.getString("assignment_status"));
            agent.setRuntimeStatus(rs.getString("runtime_status"));
            agent.setApprovalStatus(rs.getString("approval_status"));
            agent.setCapabilityCoverageTotal(rs.getInt("skill_coverage_total"));
            agent.setCapabilityCoverageMatched(rs.getInt("skill_coverage_matched"));
            agent.setMissingSkills(json.readStringList(rs.getString("missing_skills_json")));
            agent.setMissingAuthorities(json.readStringList(rs.getString("missing_authorities_json")));
            agent.setReadinessStatus(rs.getString("readiness_status"));
            agent.setUpdatedAt(offset(rs, "updated_at"));
            return agent;
        };
    }

    private static String optionalString(ResultSet rs, String column) {
        try { return rs.getString(column); } catch (SQLException ex) { return null; }
    }

    private static OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getObject(column, OffsetDateTime.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
