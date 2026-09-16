package com.opensocket.aievent.core.dispatch.flow;

import static com.opensocket.aievent.core.dispatch.flow.DispatchFlowNormalizationSupport.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;

/** Read/query side of Dispatch Flow administration, isolated from aggregate mutation. */
final class DispatchFlowQueryService {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowManagementService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final DispatchFlowRowMappers rowMappers;

    DispatchFlowQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        DispatchFlowJsonCodec json = new DispatchFlowJsonCodec(objectMapper);
        this.rowMappers = new DispatchFlowRowMappers(json);
    }

    List<DispatchFlowView> listFlows(String tenantId, String sourceSystem) { return listFlows(tenantId, sourceSystem, null); }

    List<DispatchFlowView> listFlows(String tenantId, String sourceSystem, ResourceListScopeQueryPlan scope) {
        String normalizedTenant = normalizeTenant(tenantId);
        String normalizedSource = normalizeNullable(sourceSystem);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalizedTenant);
        StringBuilder sql = new StringBuilder("""
                select
                    f.tenant_id,
                    f.flow_id,
                    f.flow_code,
                    f.flow_name,
                    f.source_system,
                    f.owner_department_id,
                    f.owner_group_id,
                    f.flow_type,
                    f.default_pool_id,
                    f.status,
                    f.description,
                    f.default_capability_requirement_mode,
                    f.default_required_operation,
                    f.default_side_effect_level,
                    f.default_candidate_pool_mode,
                    f.default_routing_strategy,
                    f.issue_sync_policy,
                    f.metadata_json,
                    f.version,
                    f.updated_by,
                    f.updated_at,
                    coalesce(sum(case when upper(coalesce(p.event_stage, 'EXTERNAL')) = 'EXTERNAL' then 1 else 0 end), 0)::int as external_rule_count,
                    coalesce(sum(case when upper(coalesce(p.event_stage, 'EXTERNAL')) = 'A2A' then 1 else 0 end), 0)::int as a2a_rule_count,
                    (select count(*)::int from flow_required_capabilities s where s.tenant_id = f.tenant_id and s.flow_id = f.flow_id) as capability_count,
                    (select count(*)::int from flow_agent_assignments a where a.tenant_id = f.tenant_id and a.flow_id = f.flow_id) as agent_count
                from dispatch_flows f
                left join dispatch_policies p
                  on p.tenant_id = f.tenant_id
                 and p.flow_id = f.flow_id
                where f.tenant_id = :tenantId
                """);
        if (scope != null) {
            sql.append("  and ").append(ScopedResourceSql.predicate(scope,"f","flow_id","owner_department_id","owner_group_id")).append("\n");
            ScopedResourceSql.bind(params, scope);
        }
        if (!blank(normalizedSource)) {
            sql.append("  and upper(f.source_system) = :sourceSystem\n");
            params.addValue("sourceSystem", normalizedSource);
        }
        sql.append("""
                group by f.tenant_id, f.flow_id, f.flow_code, f.flow_name, f.source_system, f.owner_department_id, f.owner_group_id,
                         f.flow_type, f.default_pool_id, f.status, f.description, f.default_capability_requirement_mode,
                                  f.default_required_operation, f.default_side_effect_level,
                                  f.default_candidate_pool_mode, f.default_routing_strategy, f.issue_sync_policy, f.metadata_json, f.version, f.updated_by, f.updated_at
                order by f.updated_at desc, f.flow_code asc
                """);
        try {
            List<DispatchFlowView> flows = jdbc.query(sql.toString(), params, rowMappers.flow());
            log.info("dispatch_flow_list_loaded tenantId={} sourceSystem={} flowCount={}", normalizedTenant, blank(normalizedSource) ? "*" : normalizedSource, flows.size());
            flows.forEach(this::attachChildren);
            return flows;
        } catch (DataAccessException ex) {
            log.error("dispatch_flow_list_failed tenantId={} sourceSystem={} exception={} message={}", normalizedTenant, blank(normalizedSource) ? "*" : normalizedSource, ex.getClass().getSimpleName(), safeMessage(ex), ex);
            throw ex;
        }
    }

    List<DispatchFlowView> listFlowsForAgent(String tenantId, String agentId) {
        return listFlowsForAgent(tenantId, agentId, null);
    }

    List<DispatchFlowView> listFlowsForAgent(String tenantId, String agentId, ResourceListScopeQueryPlan scope) {
        String normalizedTenant = normalizeTenant(tenantId);
        String normalizedAgentId = requireNonBlank(agentId, "agentId");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalizedTenant)
                .addValue("agentId", normalizedAgentId);
        StringBuilder sql = new StringBuilder("""
                select
                    f.tenant_id,f.flow_id,f.flow_code,f.flow_name,f.source_system,f.owner_department_id,f.owner_group_id,
                    f.flow_type,f.default_pool_id,f.status,f.description,f.default_capability_requirement_mode,
                    f.default_required_operation,f.default_side_effect_level,f.default_candidate_pool_mode,
                    f.default_routing_strategy,f.issue_sync_policy,f.metadata_json,f.version,f.updated_by,f.updated_at,
                    coalesce(sum(case when upper(coalesce(p.event_stage, 'EXTERNAL')) = 'EXTERNAL' then 1 else 0 end), 0)::int as external_rule_count,
                    coalesce(sum(case when upper(coalesce(p.event_stage, 'EXTERNAL')) = 'A2A' then 1 else 0 end), 0)::int as a2a_rule_count,
                    (select count(*)::int from flow_required_capabilities c where c.tenant_id=f.tenant_id and c.flow_id=f.flow_id) as capability_count,
                    (select count(*)::int from flow_agent_assignments a where a.tenant_id=f.tenant_id and a.flow_id=f.flow_id) as agent_count
                  from dispatch_flows f
                  left join dispatch_policies p on p.tenant_id=f.tenant_id and p.flow_id=f.flow_id
                 where f.tenant_id=:tenantId
                   and exists (select 1 from flow_agent_assignments faa where faa.tenant_id=f.tenant_id and faa.flow_id=f.flow_id and faa.agent_id=:agentId)
                """);
        if (scope != null) {
            sql.append(" and ").append(ScopedResourceSql.predicate(scope, "f", "flow_id", "owner_department_id", "owner_group_id")).append("\n");
            ScopedResourceSql.bind(params, scope);
        }
        sql.append("""
                 group by f.tenant_id,f.flow_id,f.flow_code,f.flow_name,f.source_system,f.owner_department_id,f.owner_group_id,
                          f.flow_type,f.default_pool_id,f.status,f.description,f.default_capability_requirement_mode,
                          f.default_required_operation,f.default_side_effect_level,f.default_candidate_pool_mode,
                          f.default_routing_strategy,f.issue_sync_policy,f.metadata_json,f.version,f.updated_by,f.updated_at
                 order by f.updated_at desc,f.flow_code asc
                """);
        List<DispatchFlowView> flows = jdbc.query(sql.toString(), params, rowMappers.flow());
        flows.forEach(this::attachChildren);
        log.info("dispatch_flow_list_for_agent_loaded tenantId={} agentId={} flowCount={}", normalizedTenant, normalizedAgentId, flows.size());
        return flows;
    }

    List<DispatchFlowAgentOptionView> agentOptions(String tenantId) {
        String normalizedTenant = normalizeTenant(tenantId);
        List<DispatchFlowAgentOptionView> options = jdbc.query("""
                select
                    p.tenant_id,
                    p.agent_id,
                    coalesce(nullif(p.agent_name, ''), p.agent_id) as agent_name,
                    upper(coalesce(p.approval_status, 'PENDING')) as approval_status,
                    coalesce(p.enabled, false) as enabled,
                    upper(coalesce(p.risk_status, 'NORMAL')) as risk_status,
                    coalesce((
                        select upper(coalesce(a.status, 'UNKNOWN'))
                          from agents a
                         where a.agent_id = p.agent_id
                         order by a.last_heartbeat_at desc nulls last, a.updated_at desc nulls last
                         limit 1
                    ), 'UNKNOWN') as runtime_status,
                    exists (
                        select 1
                          from agents a
                         where a.agent_id = p.agent_id
                           and upper(coalesce(a.status, 'UNKNOWN')) in ('CONNECTED', 'ONLINE', 'READY', 'IDLE', 'HEALTHY')
                    ) as runtime_connected,
                    exists (
                        select 1
                          from agents a
                         where a.agent_id = p.agent_id
                           and a.last_heartbeat_at >= now() - interval '5 minutes'
                    ) as heartbeat_healthy,
                    coalesce((
                        select (coalesce(a.current_task_count, 0) < greatest(coalesce(a.max_concurrent_tasks, 1), 1))
                          from agents a
                         where a.agent_id = p.agent_id
                         order by a.last_heartbeat_at desc nulls last, a.updated_at desc nulls last
                         limit 1
                    ), true) as capacity_available,
                    coalesce((
                        select count(*)::int
                          from flow_agent_assignments faa
                          join dispatch_flows f
                            on f.tenant_id = faa.tenant_id
                           and f.flow_id = faa.flow_id
                         where faa.tenant_id = p.tenant_id
                           and faa.agent_id = p.agent_id
                           and upper(coalesce(f.status, 'DRAFT')) in ('ACTIVE', 'ENABLED')
                    ), 0) as active_flow_count
                  from agent_profiles p
                 where p.tenant_id = :tenantId
                 order by p.updated_at desc nulls last, p.agent_id asc
                """, new MapSqlParameterSource().addValue("tenantId", normalizedTenant), rowMappers.agentOption());
        log.info("dispatch_flow_agent_options_loaded tenantId={} optionCount={}", normalizedTenant, options.size());
        return options;
    }

    Optional<DispatchFlowView> findFlow(String tenantId, String flowId) {
        if (blank(flowId)) return Optional.empty();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("flowId", flowId);
        try {
            DispatchFlowView flow = jdbc.queryForObject("""
                    select
                        f.tenant_id,
                        f.flow_id,
                        f.flow_code,
                        f.flow_name,
                        f.source_system,
                        f.owner_department_id,
                        f.owner_group_id,
                        f.flow_type,
                        f.default_pool_id,
                        f.status,
                        f.description,
                        f.default_capability_requirement_mode,
                        f.default_required_operation,
                        f.default_side_effect_level,
                        f.default_candidate_pool_mode,
                        f.default_routing_strategy,
                        f.issue_sync_policy,
                        f.metadata_json,
                        f.version,
                        f.updated_by,
                        f.updated_at,
                        coalesce(sum(case when upper(coalesce(p.event_stage, 'EXTERNAL')) = 'EXTERNAL' then 1 else 0 end), 0)::int as external_rule_count,
                        coalesce(sum(case when upper(coalesce(p.event_stage, 'EXTERNAL')) = 'A2A' then 1 else 0 end), 0)::int as a2a_rule_count,
                        (select count(*)::int from flow_required_capabilities s where s.tenant_id = f.tenant_id and s.flow_id = f.flow_id) as capability_count,
                        (select count(*)::int from flow_agent_assignments a where a.tenant_id = f.tenant_id and a.flow_id = f.flow_id) as agent_count
                    from dispatch_flows f
                    left join dispatch_policies p
                      on p.tenant_id = f.tenant_id
                     and p.flow_id = f.flow_id
                    where f.tenant_id = :tenantId
                      and f.flow_id = :flowId
                    group by f.tenant_id, f.flow_id, f.flow_code, f.flow_name, f.source_system, f.owner_department_id, f.owner_group_id,
                             f.flow_type, f.default_pool_id, f.status, f.description, f.default_capability_requirement_mode,
                                      f.default_required_operation, f.default_side_effect_level,
                                      f.default_candidate_pool_mode, f.default_routing_strategy, f.issue_sync_policy, f.metadata_json, f.version, f.updated_by, f.updated_at
                    """, params, rowMappers.flow());
            attachChildren(flow);
            return Optional.of(flow);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    List<DispatchFlowRuleView> rules(String tenantId, String flowId) {
        return jdbc.query("""
                select tenant_id, policy_id, flow_id, policy_code, policy_name, service_code, rule_scope, event_stage,
                       source_system, origin_source_system, target_system, event_type, object_type,
                       error_code, condition_json, priority, match_mode, target_pool_id, target_pool_code,
                       requested_skill, capability_requirement_mode,
                       required_operation, side_effect_level, candidate_pool_mode, routing_strategy,
                       explicit_action_authorization_required, requirement_model_version,
                       handoff_mode, issue_policy_id, issue_sync_policy, status, updated_at, metadata_json
                  from dispatch_policies
                 where tenant_id = :tenantId and flow_id = :flowId
                 order by event_stage asc, policy_code asc
                """, params(tenantId, flowId), rowMappers.rule());
    }

    List<Map<String,Object>> ruleConflicts(String tenantId, String flowId) {
        return jdbc.queryForList("""
                select tenant_id, flow_id, left_flow_id, right_flow_id, left_rule_id, right_rule_id, priority, conflict_type, evidence
                  from flow_rule_conflicts_v201
                 where tenant_id=:tenantId and (left_flow_id=:flowId or right_flow_id=:flowId)
                 order by priority asc,left_flow_id asc,right_flow_id asc,left_rule_id asc,right_rule_id asc
                """, params(tenantId, flowId));
    }

    List<DispatchFlowRequiredSkillView> skills(String tenantId, String flowId) {
        return jdbc.query("""
                select tenant_id, id, flow_id, rule_id, event_stage, agent_role, skill_code,
                       skill_name, skill_kind, authority_code, required, openclaw_skill, description
                  from flow_required_capabilities
                 where tenant_id = :tenantId and flow_id = :flowId
                 order by event_stage asc, agent_role asc, skill_code asc
                """, params(tenantId, flowId), rowMappers.capability());
    }

    List<DispatchFlowAgentView> agents(String tenantId, String flowId) {
        return jdbc.query("""
                select tenant_id, id, flow_id, agent_id, agent_name, event_stage, agent_role,
                       assignment_status, runtime_status, approval_status, skill_coverage_total,
                       skill_coverage_matched, missing_skills_json, missing_authorities_json,
                       readiness_status, updated_at
                  from flow_agent_assignments
                 where tenant_id = :tenantId and flow_id = :flowId
                 order by event_stage asc, agent_role asc, agent_id asc
                """, params(tenantId, flowId), rowMappers.agent());
    }

    private void attachChildren(DispatchFlowView flow) {
        if (flow == null) return;
        flow.setRules(rules(flow.getTenantId(), flow.getFlowId()));
        flow.setRequiredSkills(skills(flow.getTenantId(), flow.getFlowId()));
        flow.setAgents(agents(flow.getTenantId(), flow.getFlowId()));
        flow.setExternalRuleCount((int) flow.getRules().stream().filter(rule -> "EXTERNAL".equalsIgnoreCase(firstNonBlank(rule.getEventStage(), "EXTERNAL"))).count());
        flow.setA2aRuleCount((int) flow.getRules().stream().filter(rule -> "A2A".equalsIgnoreCase(rule.getEventStage())).count());
        flow.setCapabilityCount(flow.getRequiredSkills().size());
        flow.setAgentCount(flow.getAgents().size());
        Map<String, Object> metadata = new LinkedHashMap<>(flow.getMetadata());
        metadata.put("p1DbBackedCrud", true);
        flow.setMetadata(metadata);
    }

    private MapSqlParameterSource params(String tenantId, String flowId) {
        return new MapSqlParameterSource().addValue("tenantId", normalizeTenant(tenantId)).addValue("flowId", flowId);
    }
}
