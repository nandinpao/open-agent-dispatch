package com.opensocket.aievent.core.dispatch.flow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.routing.governance.CapabilityRequirementMode;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;
import com.opensocket.aievent.core.task.TaskRepository;

@Service
public class DispatchFlowManagementService {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowManagementService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private TaskRepository taskRepository;

    public DispatchFlowManagementService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<AgentPoolView> listAgentPools(String tenantId, String sourceSystem) {
        String normalizedTenant = normalizeTenant(tenantId);
        String normalizedSource = normalizeNullable(sourceSystem);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", normalizedTenant);
        StringBuilder sql = new StringBuilder("""
                select
                    p.tenant_id,
                    p.pool_id,
                    p.pool_code,
                    p.pool_name,
                    p.source_system,
                    p.pool_type,
                    p.selection_strategy,
                    p.status,
                    p.description,
                    p.metadata_json,
                    p.updated_at,
                    coalesce((
                        select count(*)::int
                          from agent_pool_members m
                         where m.tenant_id = p.tenant_id
                           and m.pool_id = p.pool_id
                           and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                    ), 0) as member_count,
                    coalesce((
                        select count(*)::int
                          from agent_pool_members m
                          join agent_profiles ap
                            on ap.tenant_id = m.tenant_id
                           and ap.agent_id = m.agent_id
                         where m.tenant_id = p.tenant_id
                           and m.pool_id = p.pool_id
                           and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                           and upper(coalesce(ap.approval_status, 'PENDING')) in ('APPROVED', 'ACTIVE')
                           and coalesce(ap.enabled, false) = true
                    ), 0) as available_agent_count
                  from agent_pools p
                 where p.tenant_id = :tenantId
                """);
        if (!blank(normalizedSource)) {
            sql.append(" and upper(p.source_system) = :sourceSystem\n");
            params.addValue("sourceSystem", normalizedSource);
        }
        sql.append(" order by p.source_system nulls last, p.pool_type asc, p.pool_code asc");
        List<AgentPoolView> pools = jdbc.query(sql.toString(), params, AGENT_POOL_ROW_MAPPER);
        pools.forEach(pool -> pool.setMembers(agentPoolMembers(normalizedTenant, pool.getPoolId())));
        log.info("agent_pool_list_loaded tenantId={} sourceSystem={} poolCount={}", normalizedTenant, blank(normalizedSource) ? "*" : normalizedSource, pools.size());
        return pools;
    }

    public Optional<AgentPoolView> findAgentPool(String tenantId, String poolId) {
        if (blank(poolId)) return Optional.empty();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("poolId", poolId.trim());
        try {
            AgentPoolView pool = jdbc.queryForObject("""
                    select
                        p.tenant_id,
                        p.pool_id,
                        p.pool_code,
                        p.pool_name,
                        p.source_system,
                        p.pool_type,
                        p.selection_strategy,
                        p.status,
                        p.description,
                        p.metadata_json,
                        p.updated_at,
                        coalesce((
                            select count(*)::int
                              from agent_pool_members m
                             where m.tenant_id = p.tenant_id
                               and m.pool_id = p.pool_id
                               and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                        ), 0) as member_count,
                        coalesce((
                            select count(*)::int
                              from agent_pool_members m
                              join agent_profiles ap
                                on ap.tenant_id = m.tenant_id
                               and ap.agent_id = m.agent_id
                             where m.tenant_id = p.tenant_id
                               and m.pool_id = p.pool_id
                               and upper(coalesce(m.member_status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                               and upper(coalesce(ap.approval_status, 'PENDING')) in ('APPROVED', 'ACTIVE')
                               and coalesce(ap.enabled, false) = true
                        ), 0) as available_agent_count
                      from agent_pools p
                     where p.tenant_id = :tenantId
                       and p.pool_id = :poolId
                    """, params, AGENT_POOL_ROW_MAPPER);
            pool.setMembers(agentPoolMembers(pool.getTenantId(), pool.getPoolId()));
            return Optional.of(pool);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public AgentPoolView createOrUpdateAgentPool(AgentPoolView request) {
        AgentPoolView normalized = normalizeAgentPool(request);
        acquirePoolLock(normalized.getTenantId(), normalized.getPoolId());
        validateAgentPoolMembers(normalized);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalized.getTenantId())
                .addValue("poolId", normalized.getPoolId())
                .addValue("poolCode", normalized.getPoolCode())
                .addValue("poolName", normalized.getPoolName())
                .addValue("sourceSystem", normalizeNullable(normalized.getSourceSystem()))
                .addValue("poolType", normalized.getPoolType())
                .addValue("selectionStrategy", normalized.getSelectionStrategy())
                .addValue("status", normalized.getStatus())
                .addValue("description", normalized.getDescription())
                .addValue("metadataJson", writeJson(normalized.getMetadata()));
        jdbc.update("""
                insert into agent_pools (
                    tenant_id, pool_id, pool_code, pool_name, source_system,
                    pool_type, selection_strategy, status, description, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :poolId, :poolCode, :poolName, :sourceSystem,
                    :poolType, :selectionStrategy, :status, :description, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, pool_id) do update set
                    pool_code = excluded.pool_code,
                    pool_name = excluded.pool_name,
                    source_system = excluded.source_system,
                    pool_type = excluded.pool_type,
                    selection_strategy = excluded.selection_strategy,
                    status = excluded.status,
                    description = excluded.description,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                """, params);
        jdbc.update("delete from agent_pool_members where tenant_id = :tenantId and pool_id = :poolId", params);
        for (AgentPoolMemberView member : normalized.getMembers()) {
            writeAgentPoolMember(normalized, member);
        }
        int awakened = taskRepository == null ? 0 : taskRepository.wakeConfigurationBlockedTasks(
                normalized.getTenantId(), normalized.getSourceSystem(), OffsetDateTime.now(),
                "Agent Pool configuration changed: " + normalized.getPoolCode());
        log.info("agent_pool_saved tenantId={} poolId={} poolCode={} sourceSystem={} memberCount={} awakenedTasks={}",
                normalized.getTenantId(), normalized.getPoolId(), normalized.getPoolCode(), normalized.getSourceSystem(),
                normalized.getMembers().size(), awakened);
        return findAgentPool(normalized.getTenantId(), normalized.getPoolId())
                .orElseThrow(() -> new IllegalStateException("Agent Pool was not readable after save: " + normalized.getPoolId()));
    }

    @Transactional
    public void retireAgentPool(String tenantId, String poolId) {
        jdbc.update("""
                update agent_pools
                   set status = 'RETIRED', updated_at = now()
                 where tenant_id = :tenantId and pool_id = :poolId
                """, new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("poolId", requireNonBlank(poolId, "poolId")));
    }

    public List<DispatchFlowView> listFlows(String tenantId, String sourceSystem) {
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
                    f.flow_type,
                    f.default_pool_id,
                    f.status,
                    f.description,
                    f.default_capability_requirement_mode,
                    f.default_required_operation,
                    f.default_side_effect_level,
                    f.default_candidate_pool_mode,
                    f.default_routing_strategy,
                    f.metadata_json,
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
        if (!blank(normalizedSource)) {
            sql.append("  and upper(f.source_system) = :sourceSystem\n");
            params.addValue("sourceSystem", normalizedSource);
        }
        sql.append("""
                group by f.tenant_id, f.flow_id, f.flow_code, f.flow_name, f.source_system,
                         f.flow_type, f.default_pool_id, f.status, f.description, f.default_capability_requirement_mode,
                                  f.default_required_operation, f.default_side_effect_level,
                                  f.default_candidate_pool_mode, f.default_routing_strategy, f.metadata_json, f.updated_at
                order by f.updated_at desc, f.flow_code asc
                """);
        try {
            List<DispatchFlowView> flows = jdbc.query(sql.toString(), params, FLOW_ROW_MAPPER);
            log.info("dispatch_flow_list_loaded tenantId={} sourceSystem={} flowCount={}", normalizedTenant, blank(normalizedSource) ? "*" : normalizedSource, flows.size());
            flows.forEach(this::attachChildren);
            return flows;
        } catch (DataAccessException ex) {
            log.error("dispatch_flow_list_failed tenantId={} sourceSystem={} exception={} message={}", normalizedTenant, blank(normalizedSource) ? "*" : normalizedSource, ex.getClass().getSimpleName(), safeMessage(ex), ex);
            throw ex;
        }
    }

    public List<DispatchFlowView> listFlowsForAgent(String tenantId, String agentId) {
        String normalizedTenant = normalizeTenant(tenantId);
        String normalizedAgentId = requireNonBlank(agentId, "agentId");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalizedTenant)
                .addValue("agentId", normalizedAgentId);
        List<DispatchFlowView> flows = jdbc.query("""
                select
                    f.tenant_id,
                    f.flow_id,
                    f.flow_code,
                    f.flow_name,
                    f.source_system,
                    f.flow_type,
                    f.default_pool_id,
                    f.status,
                    f.description,
                    f.default_capability_requirement_mode,
                    f.default_required_operation,
                    f.default_side_effect_level,
                    f.default_candidate_pool_mode,
                    f.default_routing_strategy,
                    f.metadata_json,
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
                   and exists (
                       select 1
                         from flow_agent_assignments faa
                        where faa.tenant_id = f.tenant_id
                          and faa.flow_id = f.flow_id
                          and faa.agent_id = :agentId
                   )
                 group by f.tenant_id, f.flow_id, f.flow_code, f.flow_name, f.source_system,
                          f.flow_type, f.default_pool_id, f.status, f.description, f.default_capability_requirement_mode,
                          f.default_required_operation, f.default_side_effect_level,
                          f.default_candidate_pool_mode, f.default_routing_strategy, f.metadata_json, f.updated_at
                 order by f.updated_at desc, f.flow_code asc
                """, params, FLOW_ROW_MAPPER);
        flows.forEach(this::attachChildren);
        log.info("dispatch_flow_list_for_agent_loaded tenantId={} agentId={} flowCount={}", normalizedTenant, normalizedAgentId, flows.size());
        return flows;
    }

    public List<DispatchFlowAgentOptionView> agentOptions(String tenantId) {
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
                """, new MapSqlParameterSource().addValue("tenantId", normalizedTenant), AGENT_OPTION_ROW_MAPPER);
        log.info("dispatch_flow_agent_options_loaded tenantId={} optionCount={}", normalizedTenant, options.size());
        return options;
    }

    public Optional<DispatchFlowView> findFlow(String tenantId, String flowId) {
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
                        f.flow_type,
                        f.default_pool_id,
                        f.status,
                        f.description,
                        f.default_capability_requirement_mode,
                        f.default_required_operation,
                        f.default_side_effect_level,
                        f.default_candidate_pool_mode,
                        f.default_routing_strategy,
                        f.metadata_json,
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
                    group by f.tenant_id, f.flow_id, f.flow_code, f.flow_name, f.source_system,
                             f.flow_type, f.default_pool_id, f.status, f.description, f.default_capability_requirement_mode,
                                      f.default_required_operation, f.default_side_effect_level,
                                      f.default_candidate_pool_mode, f.default_routing_strategy, f.metadata_json, f.updated_at
                    """, params, FLOW_ROW_MAPPER);
            attachChildren(flow);
            return Optional.of(flow);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    /**
     * Saves the complete Dispatch Flow aggregate in one transaction.
     *
     * <p>The request is authoritative for Flow-owned Rules, required Capabilities, and Agent
     * selections. Existing children that are not present in the request are removed in the same
     * transaction. A failure in any child write rolls the parent and every child back.</p>
     */
    @Transactional
    public DispatchFlowView createOrUpdateFlow(DispatchFlowView request) {
        DispatchFlowView normalized = normalizeFlowAggregate(request);
        validateAggregate(normalized);

        MapSqlParameterSource identity = params(normalized.getTenantId(), normalized.getFlowId());
        acquireAggregateLock(normalized.getTenantId(), normalized.getFlowId());
        writeFlow(normalized);

        // Full replacement semantics prevent half-configured Flows and stale Runtime candidates.
        jdbc.update("delete from flow_agent_assignments where tenant_id = :tenantId and flow_id = :flowId", identity);
        jdbc.update("delete from flow_required_capabilities where tenant_id = :tenantId and flow_id = :flowId", identity);
        jdbc.update("delete from dispatch_policies where tenant_id = :tenantId and flow_id = :flowId", identity);

        for (DispatchFlowRuleView rule : normalized.getRules()) {
            writeRule(rule);
        }
        for (DispatchFlowRequiredSkillView capability : normalized.getRequiredSkills()) {
            writeCapability(capability);
        }
        for (DispatchFlowAgentView agent : normalized.getAgents()) {
            writeAgent(agent);
        }

        DispatchFlowView saved = findFlow(normalized.getTenantId(), normalized.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Dispatch Flow aggregate was not readable after save: " + normalized.getFlowId()));
        verifyPersistedAggregate(normalized, saved);
        int awakened = taskRepository == null ? 0 : taskRepository.wakeConfigurationBlockedTasks(
                normalized.getTenantId(), normalized.getSourceSystem(), OffsetDateTime.now(),
                "Dispatch Flow configuration changed: " + normalized.getFlowId());
        log.info("dispatch_flow_configuration_tasks_awakened tenantId={} flowId={} sourceSystem={} taskCount={}",
                normalized.getTenantId(), normalized.getFlowId(), normalized.getSourceSystem(), awakened);
        log.info("dispatch_flow_aggregate_saved tenantId={} flowId={} flowCode={} sourceSystem={} ruleCount={} capabilityCount={} agentCount={} transactionMode=FULL_REPLACEMENT",
                normalized.getTenantId(), normalized.getFlowId(), normalized.getFlowCode(), normalized.getSourceSystem(),
                normalized.getRules().size(), normalized.getRequiredSkills().size(), normalized.getAgents().size());
        return saved;
    }

    private void writeFlow(DispatchFlowView normalized) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", normalized.getTenantId())
                .addValue("flowId", normalized.getFlowId())
                .addValue("flowCode", normalized.getFlowCode())
                .addValue("flowName", normalized.getFlowName())
                .addValue("sourceSystem", normalized.getSourceSystem())
                .addValue("flowType", firstNonBlank(normalized.getFlowType(), "SOURCE_FLOW"))
                .addValue("defaultPoolId", preserveNullableId(normalized.getDefaultPoolId()))
                .addValue("status", normalized.getStatus())
                .addValue("description", normalized.getDescription())
                .addValue("defaultCapabilityRequirementMode", normalized.getDefaultCapabilityRequirementMode())
                .addValue("defaultRequiredOperation", normalized.getDefaultRequiredOperation())
                .addValue("defaultSideEffectLevel", normalized.getDefaultSideEffectLevel())
                .addValue("defaultCandidatePoolMode", normalized.getDefaultCandidatePoolMode())
                .addValue("defaultRoutingStrategy", normalized.getDefaultRoutingStrategy())
                .addValue("metadataJson", writeJson(normalized.getMetadata()));
        jdbc.update("""
                insert into dispatch_flows (
                    tenant_id, flow_id, flow_code, flow_name, source_system,
                    flow_type, default_pool_id, status, description, default_capability_requirement_mode,
                    default_required_operation, default_side_effect_level,
                    default_candidate_pool_mode, default_routing_strategy, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :flowId, :flowCode, :flowName, :sourceSystem,
                    :flowType, :defaultPoolId, :status, :description, :defaultCapabilityRequirementMode,
                    :defaultRequiredOperation, :defaultSideEffectLevel,
                    :defaultCandidatePoolMode, :defaultRoutingStrategy, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, flow_id) do update set
                    flow_code = excluded.flow_code,
                    flow_name = excluded.flow_name,
                    source_system = excluded.source_system,
                    flow_type = excluded.flow_type,
                    default_pool_id = excluded.default_pool_id,
                    status = excluded.status,
                    description = excluded.description,
                    default_capability_requirement_mode = excluded.default_capability_requirement_mode,
                    default_required_operation = excluded.default_required_operation,
                    default_side_effect_level = excluded.default_side_effect_level,
                    default_candidate_pool_mode = excluded.default_candidate_pool_mode,
                    default_routing_strategy = excluded.default_routing_strategy,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                """, params);
    }

    private DispatchFlowRuleView writeRule(DispatchFlowRuleView rule) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", rule.getTenantId())
                .addValue("ruleId", rule.getRuleId())
                .addValue("ruleCode", rule.getRuleCode())
                .addValue("ruleName", rule.getRuleName())
                .addValue("description", "Flow-owned Dispatch Rule: " + rule.getRuleName())
                .addValue("status", Boolean.TRUE.equals(rule.getEnabled()) ? "ACTIVE" : "DRAFT")
                .addValue("flowId", rule.getFlowId())
                .addValue("ruleScope", rule.getRuleScope())
                .addValue("eventStage", rule.getEventStage())
                .addValue("sourceSystem", rule.getSourceSystem())
                .addValue("originSourceSystem", normalizeNullable(rule.getOriginSourceSystem()))
                .addValue("targetSystem", normalizeNullable(rule.getTargetSystem()))
                .addValue("eventType", normalizeWildcard(rule.getEventType()))
                .addValue("objectType", normalizeWildcard(rule.getObjectType()))
                .addValue("errorCode", normalizeWildcard(rule.getErrorCode()))
                .addValue("conditionJson", writeJson(rule.getCondition()))
                .addValue("priority", rule.getPriority() == null ? 100 : rule.getPriority())
                .addValue("matchMode", firstNonBlank(rule.getMatchMode(), "EXACT_OR_WILDCARD"))
                .addValue("targetPoolId", preserveNullableId(rule.getTargetPoolId()))
                .addValue("targetPoolCode", normalizeNullable(rule.getTargetPoolCode()))
                .addValue("requestedSkill", rule.getRequestedSkill())
                .addValue("capabilityRequirementMode", rule.getCapabilityRequirementMode())
                .addValue("requiredOperation", rule.getRequiredOperation())
                .addValue("sideEffectLevel", rule.getSideEffectLevel())
                .addValue("candidatePoolMode", rule.getCandidatePoolMode())
                .addValue("routingStrategy", rule.getRoutingStrategy())
                .addValue("explicitActionAuthorizationRequired", rule.getExplicitActionAuthorizationRequired())
                .addValue("requirementModelVersion", rule.getRequirementModelVersion())
                .addValue("handoffMode", normalizeNullable(rule.getHandoffMode()))
                .addValue("issuePolicyId", rule.getIssuePolicyId())
                .addValue("metadataJson", writeJson(Map.of(
                        "p1DbBackedCrud", true,
                        "phase32bTargetPoolPersistence", true,
                        "priority", rule.getPriority() == null ? 100 : rule.getPriority())));
        jdbc.update("""
                insert into dispatch_policies (
                    tenant_id, policy_id, policy_code, policy_name, description,
                    risk_level, status, version, metadata_json,
                    flow_id, rule_scope, event_stage, source_system, origin_source_system,
                    target_system, event_type, object_type, error_code, condition_json,
                    priority, match_mode, target_pool_id, target_pool_code,
                    requested_skill, capability_requirement_mode, required_operation,
                    side_effect_level, candidate_pool_mode, routing_strategy,
                    explicit_action_authorization_required, requirement_model_version,
                    handoff_mode, issue_policy_id,
                    created_at, updated_at
                ) values (
                    :tenantId, :ruleId, :ruleCode, :ruleName, :description,
                    'MIDDLE', :status, 1, cast(:metadataJson as jsonb),
                    :flowId, :ruleScope, :eventStage, :sourceSystem, :originSourceSystem,
                    :targetSystem, :eventType, :objectType, :errorCode, cast(:conditionJson as jsonb),
                    :priority, :matchMode, :targetPoolId, :targetPoolCode,
                    :requestedSkill, :capabilityRequirementMode, :requiredOperation,
                    :sideEffectLevel, :candidatePoolMode, :routingStrategy,
                    :explicitActionAuthorizationRequired, :requirementModelVersion,
                    :handoffMode, :issuePolicyId,
                    now(), now()
                )
                on conflict (policy_id) do update set
                    policy_code = excluded.policy_code,
                    policy_name = excluded.policy_name,
                    description = excluded.description,
                    status = excluded.status,
                    metadata_json = excluded.metadata_json,
                    flow_id = excluded.flow_id,
                    rule_scope = excluded.rule_scope,
                    event_stage = excluded.event_stage,
                    source_system = excluded.source_system,
                    origin_source_system = excluded.origin_source_system,
                    target_system = excluded.target_system,
                    event_type = excluded.event_type,
                    object_type = excluded.object_type,
                    error_code = excluded.error_code,
                    condition_json = excluded.condition_json,
                    priority = excluded.priority,
                    match_mode = excluded.match_mode,
                    target_pool_id = excluded.target_pool_id,
                    target_pool_code = excluded.target_pool_code,
                    requested_skill = excluded.requested_skill,
                    capability_requirement_mode = excluded.capability_requirement_mode,
                    required_operation = excluded.required_operation,
                    side_effect_level = excluded.side_effect_level,
                    candidate_pool_mode = excluded.candidate_pool_mode,
                    routing_strategy = excluded.routing_strategy,
                    explicit_action_authorization_required = excluded.explicit_action_authorization_required,
                    requirement_model_version = excluded.requirement_model_version,
                    handoff_mode = excluded.handoff_mode,
                    issue_policy_id = excluded.issue_policy_id,
                    updated_at = now()
                where dispatch_policies.tenant_id = excluded.tenant_id
                  and dispatch_policies.flow_id = excluded.flow_id
                """, params);
        log.info("dispatch_flow_rule_upserted tenantId={} flowId={} ruleId={} ruleCode={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requestedSkill={} status={}",
                rule.getTenantId(), rule.getFlowId(), rule.getRuleId(), rule.getRuleCode(), rule.getSourceSystem(), rule.getEventStage(),
                rule.getObjectType(), rule.getEventType(), rule.getErrorCode(), rule.getRequestedSkill(), Boolean.TRUE.equals(rule.getEnabled()) ? "ACTIVE" : "DRAFT");
        return rule;
    }

    private DispatchFlowRequiredSkillView writeCapability(DispatchFlowRequiredSkillView capability) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", capability.getTenantId())
                .addValue("id", capability.getId())
                .addValue("flowId", capability.getFlowId())
                .addValue("ruleId", emptyToNull(capability.getRuleId()))
                .addValue("eventStage", capability.getEventStage())
                .addValue("agentRole", capability.getAgentRole())
                .addValue("capabilityCode", capability.getSkillCode())
                .addValue("capabilityName", capability.getSkillName())
                .addValue("capabilityKind", capability.getSkillKind())
                .addValue("authorityCode", capability.getAuthorityCode())
                .addValue("required", capability.getRequired())
                .addValue("openClawCapability", capability.getOpenClawCapability())
                .addValue("description", capability.getDescription())
                .addValue("metadataJson", writeJson(Map.of("p1DbBackedCrud", true)));
        jdbc.update("""
                insert into flow_required_capabilities (
                    tenant_id, id, flow_id, rule_id, event_stage, agent_role,
                    skill_code, authority_code, required, metadata_json,
                    skill_name, skill_kind, openclaw_skill, description,
                    created_at, updated_at
                ) values (
                    :tenantId, :id, :flowId, :ruleId, :eventStage, :agentRole,
                    :capabilityCode, :authorityCode, :required, cast(:metadataJson as jsonb),
                    :capabilityName, :capabilityKind, :openClawCapability, :description,
                    now(), now()
                )
                on conflict (tenant_id, id) do update set
                    flow_id = excluded.flow_id,
                    rule_id = excluded.rule_id,
                    event_stage = excluded.event_stage,
                    agent_role = excluded.agent_role,
                    skill_code = excluded.skill_code,
                    authority_code = excluded.authority_code,
                    required = excluded.required,
                    metadata_json = excluded.metadata_json,
                    skill_name = excluded.skill_name,
                    skill_kind = excluded.skill_kind,
                    openclaw_skill = excluded.openclaw_skill,
                    description = excluded.description,
                    updated_at = now()
                where flow_required_capabilities.flow_id = excluded.flow_id
                """, params);
        log.info("dispatch_flow_capability_upserted tenantId={} flowId={} id={} eventStage={} capabilityCode={} required={}",
                capability.getTenantId(), capability.getFlowId(), capability.getId(), capability.getEventStage(), capability.getSkillCode(), capability.getRequired());
        return capability;
    }

    private DispatchFlowAgentView writeAgent(DispatchFlowAgentView agent) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", agent.getTenantId())
                .addValue("id", agent.getId())
                .addValue("flowId", agent.getFlowId())
                .addValue("agentId", agent.getAgentId())
                .addValue("agentName", agent.getAgentName())
                .addValue("eventStage", agent.getEventStage())
                .addValue("agentRole", agent.getAgentRole())
                .addValue("assignmentStatus", agent.getAssignmentStatus())
                .addValue("runtimeStatus", agent.getRuntimeStatus())
                .addValue("approvalStatus", agent.getApprovalStatus())
                .addValue("capabilityCoverageTotal", agent.getCapabilityCoverageTotal() == null ? 0 : agent.getCapabilityCoverageTotal())
                .addValue("capabilityCoverageMatched", agent.getCapabilityCoverageMatched() == null ? 0 : agent.getCapabilityCoverageMatched())
                .addValue("missingCapabilitysJson", writeJson(agent.getMissingSkills()))
                .addValue("missingAuthoritiesJson", writeJson(agent.getMissingAuthorities()))
                .addValue("readinessStatus", agent.getReadinessStatus())
                .addValue("metadataJson", writeJson(Map.of("p1DbBackedCrud", true)));
        jdbc.update("""
                insert into flow_agent_assignments (
                    tenant_id, id, flow_id, agent_id, agent_name, event_stage, agent_role,
                    assignment_status, runtime_status, approval_status, skill_coverage_total,
                    skill_coverage_matched, missing_skills_json, missing_authorities_json,
                    readiness_status, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :id, :flowId, :agentId, :agentName, :eventStage, :agentRole,
                    :assignmentStatus, :runtimeStatus, :approvalStatus, :capabilityCoverageTotal,
                    :capabilityCoverageMatched, cast(:missingCapabilitysJson as jsonb), cast(:missingAuthoritiesJson as jsonb),
                    :readinessStatus, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, id) do update set
                    flow_id = excluded.flow_id,
                    agent_id = excluded.agent_id,
                    agent_name = excluded.agent_name,
                    event_stage = excluded.event_stage,
                    agent_role = excluded.agent_role,
                    assignment_status = excluded.assignment_status,
                    runtime_status = excluded.runtime_status,
                    approval_status = excluded.approval_status,
                    skill_coverage_total = excluded.skill_coverage_total,
                    skill_coverage_matched = excluded.skill_coverage_matched,
                    missing_skills_json = excluded.missing_skills_json,
                    missing_authorities_json = excluded.missing_authorities_json,
                    readiness_status = excluded.readiness_status,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                where flow_agent_assignments.flow_id = excluded.flow_id
                """, params);
        log.info("dispatch_flow_agent_upserted tenantId={} flowId={} id={} agentId={} eventStage={} assignmentStatus={} approvalStatus={} readinessStatus={}",
                agent.getTenantId(), agent.getFlowId(), agent.getId(), agent.getAgentId(), agent.getEventStage(),
                agent.getAssignmentStatus(), agent.getApprovalStatus(), agent.getReadinessStatus());
        return agent;
    }

    private void acquireAggregateLock(String tenantId, String flowId) {
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(:aggregateKey, 0))",
                new MapSqlParameterSource().addValue("aggregateKey", tenantId + ":" + flowId));
    }

    private void acquirePoolLock(String tenantId, String poolId) {
        jdbc.queryForList(
                "select pg_advisory_xact_lock(hashtextextended(:aggregateKey, 0))",
                new MapSqlParameterSource().addValue("aggregateKey", "agent-pool:" + tenantId + ":" + poolId));
    }

    private List<AgentPoolMemberView> agentPoolMembers(String tenantId, String poolId) {
        return jdbc.query("""
                select
                    m.tenant_id,
                    m.pool_id,
                    p.pool_code,
                    m.agent_id,
                    coalesce(nullif(ap.agent_name, ''), m.agent_id) as agent_name,
                    m.member_status,
                    m.priority,
                    m.weight,
                    upper(coalesce(ap.approval_status, 'PENDING')) as approval_status,
                    coalesce((
                        select upper(coalesce(a.status, 'UNKNOWN'))
                          from agents a
                         where a.agent_id = m.agent_id
                         order by a.last_heartbeat_at desc nulls last, a.updated_at desc nulls last
                         limit 1
                    ), 'UNKNOWN') as runtime_status,
                    m.metadata_json,
                    m.updated_at
                  from agent_pool_members m
                  join agent_pools p
                    on p.tenant_id = m.tenant_id
                   and p.pool_id = m.pool_id
                  left join agent_profiles ap
                    on ap.tenant_id = m.tenant_id
                   and ap.agent_id = m.agent_id
                 where m.tenant_id = :tenantId
                   and m.pool_id = :poolId
                 order by m.priority asc, m.weight desc, m.agent_id asc
                """, new MapSqlParameterSource()
                .addValue("tenantId", normalizeTenant(tenantId))
                .addValue("poolId", requireNonBlank(poolId, "poolId")), AGENT_POOL_MEMBER_ROW_MAPPER);
    }

    private AgentPoolView normalizeAgentPool(AgentPoolView request) {
        AgentPoolView pool = request == null ? new AgentPoolView() : request;
        String tenantId = normalizeTenant(pool.getTenantId());
        String source = normalizeNullable(pool.getSourceSystem());
        String poolCode = normalizeCode(firstNonBlank(pool.getPoolCode(), source == null ? null : source + "_TRIAGE_POOL", pool.getPoolName()));
        String poolId = firstNonBlank(pool.getPoolId(), "pool-" + safeIdPart(tenantId) + "-" + safeIdPart(poolCode));
        pool.setTenantId(tenantId);
        pool.setPoolId(poolId);
        pool.setPoolCode(requireNonBlank(poolCode, "poolCode"));
        pool.setPoolName(firstNonBlank(pool.getPoolName(), pool.getPoolCode().replace('_', ' ')));
        pool.setSourceSystem(source);
        pool.setPoolType(normalizeCode(firstNonBlank(pool.getPoolType(), "RESOLUTION")));
        pool.setSelectionStrategy(normalizeCode(firstNonBlank(pool.getSelectionStrategy(), "LOWEST_LOAD")));
        pool.setStatus(normalizeCode(firstNonBlank(pool.getStatus(), "ACTIVE")));
        List<AgentPoolMemberView> members = new ArrayList<>();
        for (AgentPoolMemberView rawMember : pool.getMembers()) {
            if (rawMember == null || blank(rawMember.getAgentId())) continue;
            AgentPoolMemberView member = new AgentPoolMemberView();
            member.setTenantId(tenantId);
            member.setPoolId(poolId);
            member.setPoolCode(pool.getPoolCode());
            member.setAgentId(rawMember.getAgentId().trim());
            member.setAgentName(rawMember.getAgentName());
            member.setMemberStatus(normalizeCode(firstNonBlank(rawMember.getMemberStatus(), "ACTIVE")));
            member.setPriority(rawMember.getPriority() == null ? 100 : rawMember.getPriority());
            member.setWeight(rawMember.getWeight() == null ? 1 : rawMember.getWeight());
            member.setApprovalStatus(rawMember.getApprovalStatus());
            member.setRuntimeStatus(rawMember.getRuntimeStatus());
            member.setMetadata(rawMember.getMetadata());
            members.add(member);
        }
        pool.setMembers(members);
        return pool;
    }

    private void validateAgentPoolMembers(AgentPoolView pool) {
        requireUnique(pool.getMembers().stream().map(AgentPoolMemberView::getAgentId).toList(), "agentPoolMemberAgentId");
        if (pool.getMembers().isEmpty()) return;
        List<String> agentIds = pool.getMembers().stream().map(AgentPoolMemberView::getAgentId).distinct().toList();
        List<String> existing = jdbc.queryForList("""
                select agent_id
                  from agent_profiles
                 where tenant_id = :tenantId
                   and agent_id in (:agentIds)
                """, new MapSqlParameterSource()
                .addValue("tenantId", pool.getTenantId())
                .addValue("agentIds", agentIds), String.class);
        Set<String> missing = new LinkedHashSet<>(agentIds);
        missing.removeAll(existing);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Agent Pool members do not exist in the selected tenant: " + String.join(", ", missing));
        }
    }

    private void writeAgentPoolMember(AgentPoolView pool, AgentPoolMemberView member) {
        jdbc.update("""
                insert into agent_pool_members (
                    tenant_id, pool_id, agent_id, member_status, priority, weight, metadata_json, created_at, updated_at
                ) values (
                    :tenantId, :poolId, :agentId, :memberStatus, :priority, :weight, cast(:metadataJson as jsonb), now(), now()
                )
                on conflict (tenant_id, pool_id, agent_id) do update set
                    member_status = excluded.member_status,
                    priority = excluded.priority,
                    weight = excluded.weight,
                    metadata_json = excluded.metadata_json,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("tenantId", pool.getTenantId())
                .addValue("poolId", pool.getPoolId())
                .addValue("agentId", member.getAgentId())
                .addValue("memberStatus", member.getMemberStatus())
                .addValue("priority", member.getPriority())
                .addValue("weight", member.getWeight())
                .addValue("metadataJson", writeJson(member.getMetadata())));
    }

    private DispatchFlowView normalizeFlowAggregate(DispatchFlowView request) {
        DispatchFlowView flow = normalizeFlow(request);
        List<DispatchFlowRuleView> rules = new ArrayList<>();
        for (DispatchFlowRuleView rawRule : flow.getRules()) {
            DispatchFlowRuleView rule = rawRule == null ? new DispatchFlowRuleView() : rawRule;
            validateChildIdentity(flow, rule.getTenantId(), rule.getFlowId(), "rule");
            if (blank(rule.getSourceSystem())) rule.setSourceSystem(flow.getSourceSystem());
            DispatchFlowRuleView normalizedRule = normalizeRule(flow.getTenantId(), flow.getFlowId(), rule);
            // Phase 32-G: the standard setting UI routes to Agent Pool / Work Queue first.
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
        synchronizeAgentSelections(flow);
        synchronizeFlowRequiredCapabilities(flow);
        flow.setDefaultCapabilityRequirementMode(flow.getRules().stream()
                .anyMatch(rule -> CapabilityRequirementMode.EXPLICIT.name().equals(rule.getCapabilityRequirementMode()))
                ? CapabilityRequirementMode.EXPLICIT.name()
                : CapabilityRequirementMode.NONE.name());
        return flow;
    }

    private void validateAggregate(DispatchFlowView flow) {
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
                  from agent_capability_catalog
                 where tenant_id = :tenantId
                   and upper(capability_code) in (:capabilityCodes)
                   and upper(coalesce(status, 'ACTIVE')) in ('ACTIVE', 'ENABLED')
                   and coalesce(is_dispatch_eligible, true) = true
                """, new MapSqlParameterSource()
                        .addValue("tenantId", flow.getTenantId())
                        .addValue("capabilityCodes", capabilityCodes), String.class);
        Set<String> missing = new LinkedHashSet<>(capabilityCodes);
        missing.removeAll(available.stream().map(DispatchFlowManagementService::normalizeCode).toList());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Required Capabilities are not active in this tenant's Capability Catalog: " + String.join(", ", missing));
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

    private void verifyPersistedAggregate(DispatchFlowView expected, DispatchFlowView actual) {
        if (actual.getRules().size() != expected.getRules().size()
                || actual.getRequiredSkills().size() != expected.getRequiredSkills().size()
                || actual.getAgents().size() != expected.getAgents().size()) {
            throw new IllegalStateException("Persisted Dispatch Flow aggregate counts do not match the request.");
        }
    }

    @Transactional
    public void retireFlow(String tenantId, String flowId) {
        jdbc.update("""
                update dispatch_flows
                   set status = 'RETIRED', updated_at = now()
                 where tenant_id = :tenantId and flow_id = :flowId
                """, new MapSqlParameterSource()
                        .addValue("tenantId", normalizeTenant(tenantId))
                        .addValue("flowId", requireNonBlank(flowId, "flowId")));
        jdbc.update("""
                update dispatch_policies
                   set status = 'RETIRED', updated_at = now()
                 where tenant_id = :tenantId and flow_id = :flowId
                """, new MapSqlParameterSource()
                        .addValue("tenantId", normalizeTenant(tenantId))
                        .addValue("flowId", flowId));
    }

    public List<DispatchFlowRuleView> rules(String tenantId, String flowId) {
        return jdbc.query("""
                select tenant_id, policy_id, flow_id, policy_code, policy_name, rule_scope, event_stage,
                       source_system, origin_source_system, target_system, event_type, object_type,
                       error_code, condition_json, priority, match_mode, target_pool_id, target_pool_code,
                       requested_skill, capability_requirement_mode,
                       required_operation, side_effect_level, candidate_pool_mode, routing_strategy,
                       explicit_action_authorization_required, requirement_model_version,
                       handoff_mode, issue_policy_id, status, updated_at, metadata_json
                  from dispatch_policies
                 where tenant_id = :tenantId and flow_id = :flowId
                 order by event_stage asc, policy_code asc
                """, params(tenantId, flowId), RULE_ROW_MAPPER);
    }

    public List<DispatchFlowRequiredSkillView> skills(String tenantId, String flowId) {
        return jdbc.query("""
                select tenant_id, id, flow_id, rule_id, event_stage, agent_role, skill_code,
                       skill_name, skill_kind, authority_code, required, openclaw_skill, description
                  from flow_required_capabilities
                 where tenant_id = :tenantId and flow_id = :flowId
                 order by event_stage asc, agent_role asc, skill_code asc
                """, params(tenantId, flowId), SKILL_ROW_MAPPER);
    }

    public List<DispatchFlowAgentView> agents(String tenantId, String flowId) {
        return jdbc.query("""
                select tenant_id, id, flow_id, agent_id, agent_name, event_stage, agent_role,
                       assignment_status, runtime_status, approval_status, skill_coverage_total,
                       skill_coverage_matched, missing_skills_json, missing_authorities_json,
                       readiness_status, updated_at
                  from flow_agent_assignments
                 where tenant_id = :tenantId and flow_id = :flowId
                 order by event_stage asc, agent_role asc, agent_id asc
                """, params(tenantId, flowId), AGENT_ROW_MAPPER);
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

    private DispatchFlowView normalizeFlow(DispatchFlowView request) {
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
        flow.setDefaultSideEffectLevel(normalizeCode(firstNonBlank(
                flow.getDefaultSideEffectLevel(), "NONE")));
        flow.setDefaultCandidatePoolMode(normalizeCode(firstNonBlank(
                flow.getDefaultCandidatePoolMode(), CandidatePoolMode.EXPLICIT_FLOW_AGENTS.name())));
        flow.setDefaultRoutingStrategy(normalizeCode(firstNonBlank(
                flow.getDefaultRoutingStrategy(), "WEIGHTED_SCORE")));
        return flow;
    }

    private DispatchFlowRuleView normalizeRule(String tenantId, String flowId, DispatchFlowRuleView request) {
        DispatchFlowRuleView rule = request == null ? new DispatchFlowRuleView() : request;
        String source = normalizeCode(requireNonBlank(rule.getSourceSystem(), "sourceSystem"));
        String eventStage = normalizeCode(firstNonBlank(rule.getEventStage(), "EXTERNAL"));
        String eventType = normalizeWildcard(firstNonBlank(rule.getEventType(), "*"));
        // Phase 5: requestedSkill is no longer an operator-owned dispatch selector.
        // Required Capability rows are synchronized after rule normalization.
        String requestedSkill = null;
        String capabilityRequirementMode = CapabilityRequirementMode.NONE.name();
        String ruleCode = normalizeCode(firstNonBlank(rule.getRuleCode(), source + "_" + eventStage + "_" + normalizeWildcard(eventType).replace("*", "ANY") + "_RULE"));
        rule.setTenantId(tenantId);
        rule.setFlowId(flowId);
        rule.setRuleCode(ruleCode);
        rule.setRuleId(firstNonBlank(rule.getRuleId(), "rule-" + safeIdPart(tenantId) + "-" + ruleCode.toLowerCase(Locale.ROOT).replace('_', '-')));
        rule.setRuleName(firstNonBlank(rule.getRuleName(), ruleCode.replace('_', ' ')));
        rule.setRuleScope(normalizeCode(firstNonBlank(rule.getRuleScope(), "A2A".equals(eventStage) ? "A2A_DISPATCH" : "EXTERNAL_INTAKE")));
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
        rule.setRequirementModelVersion(rule.getRequirementModelVersion() == null
                ? 10
                : Math.max(10, rule.getRequirementModelVersion()));
        rule.setEnabled(rule.getEnabled() == null ? Boolean.TRUE : rule.getEnabled());
        rule.setLegacyStatus("FLOW_OWNED_READY");
        return rule;
    }

    private DispatchFlowRequiredSkillView normalizeCapability(String tenantId, String flowId, DispatchFlowRequiredSkillView request) {
        DispatchFlowRequiredSkillView capability = request == null ? new DispatchFlowRequiredSkillView() : request;
        String eventStage = normalizeCode(firstNonBlank(capability.getEventStage(), "EXTERNAL"));
        String agentRole = normalizeCode(firstNonBlank(capability.getAgentRole(), "LEAD"));
        String capabilityCode = normalizeCode(requireNonBlank(capability.getSkillCode(), "capabilityCode"));
        capability.setTenantId(tenantId);
        capability.setFlowId(flowId);
        capability.setEventStage(eventStage);
        capability.setAgentRole(agentRole);
        capability.setSkillCode(capabilityCode);
        capability.setId(firstNonBlank(capability.getId(), "capability-" + flowId.toLowerCase(Locale.ROOT) + "-" + eventStage.toLowerCase(Locale.ROOT) + "-" + capabilityCode.toLowerCase(Locale.ROOT).replace('_', '-')));
        capability.setSkillName(firstNonBlank(capability.getSkillName(), capabilityCode.replace('_', ' ')));
        capability.setSkillKind(normalizeCode(firstNonBlank(capability.getSkillKind(), "FLOW_CAPABILITY")));
        capability.setRequired(capability.getRequired() == null ? Boolean.TRUE : capability.getRequired());
        capability.setOpenClawCapability(capability.getOpenClawCapability() == null ? Boolean.TRUE : capability.getOpenClawCapability());
        capability.setLegacyStatus("FLOW_OWNED_CAPABILITY");
        return capability;
    }

    private DispatchFlowAgentView normalizeAgent(String tenantId, String flowId, DispatchFlowAgentView request) {
        DispatchFlowAgentView agent = request == null ? new DispatchFlowAgentView() : request;
        String eventStage = normalizeCode(firstNonBlank(agent.getEventStage(), "EXTERNAL"));
        String agentRole = normalizeCode(firstNonBlank(agent.getAgentRole(), "LEAD"));
        String agentId = requireNonBlank(agent.getAgentId(), "agentId");
        agent.setTenantId(tenantId);
        agent.setFlowId(flowId);
        agent.setEventStage(eventStage);
        agent.setAgentRole(agentRole);
        agent.setAgentId(agentId);
        agent.setId(firstNonBlank(agent.getId(), "flow-agent-" + flowId.toLowerCase(Locale.ROOT) + "-" + agentId.toLowerCase(Locale.ROOT).replace('_', '-')));
        agent.setAssignmentStatus(normalizeCode(firstNonBlank(agent.getAssignmentStatus(), "ACTIVE")));
        agent.setRuntimeStatus(normalizeCode(firstNonBlank(agent.getRuntimeStatus(), "UNKNOWN")));
        agent.setApprovalStatus(normalizeCode(firstNonBlank(agent.getApprovalStatus(), "APPROVED")));
        agent.setReadinessStatus(normalizeCode(firstNonBlank(agent.getReadinessStatus(), "READY")));
        agent.setLegacyStatus("FLOW_RULE_AGENT_ASSIGNMENT");
        return agent;
    }

    private MapSqlParameterSource params(String tenantId, String flowId) {
        return new MapSqlParameterSource().addValue("tenantId", normalizeTenant(tenantId)).addValue("flowId", flowId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize Dispatch Flow JSON", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (blank(value)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> readStringList(String value) {
        if (blank(value)) return new ArrayList<>();
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }


    private final RowMapper<AgentPoolView> AGENT_POOL_ROW_MAPPER = (rs, rowNum) -> {
        AgentPoolView pool = new AgentPoolView();
        pool.setTenantId(rs.getString("tenant_id"));
        pool.setPoolId(rs.getString("pool_id"));
        pool.setPoolCode(rs.getString("pool_code"));
        pool.setPoolName(rs.getString("pool_name"));
        pool.setSourceSystem(rs.getString("source_system"));
        pool.setPoolType(rs.getString("pool_type"));
        pool.setSelectionStrategy(rs.getString("selection_strategy"));
        pool.setStatus(rs.getString("status"));
        pool.setDescription(rs.getString("description"));
        pool.setMemberCount(rs.getInt("member_count"));
        pool.setAvailableAgentCount(rs.getInt("available_agent_count"));
        pool.setMetadata(readMap(rs.getString("metadata_json")));
        pool.setUpdatedAt(offset(rs, "updated_at"));
        return pool;
    };

    private final RowMapper<AgentPoolMemberView> AGENT_POOL_MEMBER_ROW_MAPPER = (rs, rowNum) -> {
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
        member.setMetadata(readMap(rs.getString("metadata_json")));
        member.setUpdatedAt(offset(rs, "updated_at"));
        return member;
    };

    private final RowMapper<DispatchFlowAgentOptionView> AGENT_OPTION_ROW_MAPPER = (rs, rowNum) -> {
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

    private final RowMapper<DispatchFlowView> FLOW_ROW_MAPPER = (rs, rowNum) -> {
        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId(rs.getString("tenant_id"));
        flow.setFlowId(rs.getString("flow_id"));
        flow.setFlowCode(rs.getString("flow_code"));
        flow.setFlowName(rs.getString("flow_name"));
        flow.setSourceSystem(rs.getString("source_system"));
        flow.setFlowType(rs.getString("flow_type"));
        flow.setDefaultPoolId(rs.getString("default_pool_id"));
        flow.setStatus(rs.getString("status"));
        flow.setDescription(rs.getString("description"));
        flow.setDefaultCapabilityRequirementMode(rs.getString("default_capability_requirement_mode"));
        flow.setDefaultRequiredOperation(rs.getString("default_required_operation"));
        flow.setDefaultSideEffectLevel(rs.getString("default_side_effect_level"));
        flow.setDefaultCandidatePoolMode(rs.getString("default_candidate_pool_mode"));
        flow.setDefaultRoutingStrategy(rs.getString("default_routing_strategy"));
        flow.setExternalRuleCount(rs.getInt("external_rule_count"));
        flow.setA2aRuleCount(rs.getInt("a2a_rule_count"));
        flow.setCapabilityCount(rs.getInt("capability_count"));
        flow.setAgentCount(rs.getInt("agent_count"));
        flow.setLastTestStatus("NOT_RUN");
        flow.setMetadata(readMap(rs.getString("metadata_json")));
        flow.setUpdatedAt(offset(rs, "updated_at"));
        return flow;
    };

    private final RowMapper<DispatchFlowRuleView> RULE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public DispatchFlowRuleView mapRow(ResultSet rs, int rowNum) throws SQLException {
            DispatchFlowRuleView rule = new DispatchFlowRuleView();
            rule.setTenantId(rs.getString("tenant_id"));
            rule.setRuleId(rs.getString("policy_id"));
            rule.setFlowId(rs.getString("flow_id"));
            rule.setRuleCode(rs.getString("policy_code"));
            rule.setRuleName(rs.getString("policy_name"));
            rule.setRuleScope(rs.getString("rule_scope"));
            rule.setEventStage(rs.getString("event_stage"));
            rule.setSourceSystem(rs.getString("source_system"));
            rule.setOriginSourceSystem(rs.getString("origin_source_system"));
            rule.setTargetSystem(rs.getString("target_system"));
            rule.setEventType(rs.getString("event_type"));
            rule.setObjectType(rs.getString("object_type"));
            rule.setErrorCode(rs.getString("error_code"));
            rule.setCondition(readMap(rs.getString("condition_json")));
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
            rule.setEnabled(List.of("ACTIVE", "ENABLED").contains(firstNonBlank(rs.getString("status"), "").toUpperCase(Locale.ROOT)));
            Map<String, Object> metadata = readMap(rs.getString("metadata_json"));
            Object priority = metadata.get("priority");
            if (priority instanceof Number number) rule.setPriority(number.intValue());
            rule.setUpdatedAt(offset(rs, "updated_at"));
            return rule;
        }
    };

    private final RowMapper<DispatchFlowRequiredSkillView> SKILL_ROW_MAPPER = (rs, rowNum) -> {
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

    private final RowMapper<DispatchFlowAgentView> AGENT_ROW_MAPPER = (rs, rowNum) -> {
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
        agent.setMissingSkills(readStringList(rs.getString("missing_skills_json")));
        agent.setMissingAuthorities(readStringList(rs.getString("missing_authorities_json")));
        agent.setReadinessStatus(rs.getString("readiness_status"));
        agent.setUpdatedAt(offset(rs, "updated_at"));
        return agent;
    };

    private static OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getObject(column, OffsetDateTime.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) return "-";
        return ex.getMessage().replace('\n', ' ').replace('\r', ' ');
    }

    private static String normalizeTenant(String value) {
        return requireNonBlank(value, "tenantId").trim();
    }

    private static String normalizeCode(String value) {
        if (blank(value)) return null;
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return blank(value) ? null : normalizeCode(value);
    }

    /**
     * Preserve database identities such as agent_pools.pool_id.
     *
     * Phase 32 introduced Source Flow -> Agent Pool routing, where defaultPoolId
     * and targetPoolId are foreign-key-like IDs, not business codes. They must not
     * be normalized through normalizeCode(), because that converts
     * `pool-...` into `POOL_...` and makes the routing repository unable to find
     * the active pool at assignment time.
     */
    private static String preserveNullableId(String value) {
        return blank(value) ? null : value.trim();
    }

    private static String normalizeWildcard(String value) {
        if (blank(value)) return "*";
        String normalized = normalizeCode(value);
        return "ANY".equals(normalized) ? "*" : normalized;
    }

    private static String safeIdPart(String value) {
        String normalized = value == null ? "tenant" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "tenant" : normalized;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (blank(value)) throw new IllegalArgumentException(fieldName + " is required for DB-backed Dispatch Flow CRUD.");
        return value;
    }

    private static String emptyToNull(String value) {
        return blank(value) ? null : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

