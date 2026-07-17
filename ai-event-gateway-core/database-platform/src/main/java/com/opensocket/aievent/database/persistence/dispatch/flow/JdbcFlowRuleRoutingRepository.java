package com.opensocket.aievent.database.persistence.dispatch.flow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRuntimeMatch;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRuntimeQuery;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class JdbcFlowRuleRoutingRepository implements FlowRuleRoutingRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcFlowRuleRoutingRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFlowRuleRoutingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FlowRuleRuntimeMatch> findBestMatch(FlowRuleRuntimeQuery query) {
        if (query == null || blank(normalize(query.getSourceSystem())) && blank(normalize(query.getOriginSourceSystem()))) {
            return Optional.empty();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        String rawTenantId = requireNonBlank(query.getTenantId(), "tenantId").trim();
        // Stage 8-F2c: tenant identifiers are tenant-scoped business keys, not
        // source/event codes. Runtime rule lookup must compare tenant_id
        // case-insensitively while preserving delimiters such as hyphens. Do
        // not run tenantId through normalize(), because normalize() converts
        // delimiters and can make an event-carried tenant key miss the persisted
        // Dispatch Flow tenant key.
        String tenantId = rawTenantId.toUpperCase(Locale.ROOT);
        String sourceSystem = firstNonBlank(normalize(query.getSourceSystem()), normalize(query.getOriginSourceSystem()));
        String targetSystem = normalize(query.getTargetSystem());
        String eventType = normalize(query.getEventType());
        String objectType = normalize(query.getObjectType());
        String errorCode = normalize(query.getErrorCode());
        // Phase 5: incoming task.requestedSkill is no longer a selector for standard Dispatch Flow routing.
        // Required capabilities are loaded only from flow_required_capabilities after the Flow Rule matches.
        String requestedSkill = null;
        boolean hasTargetSystem = !blank(targetSystem) && !"*".equals(targetSystem);
        boolean hasEventType = !blank(eventType) && !"*".equals(eventType);
        boolean hasObjectType = !blank(objectType) && !"*".equals(objectType);
        boolean hasErrorCode = !blank(errorCode) && !"*".equals(errorCode);
        boolean hasRequestedSkill = false;
        params.put("tenantIds", tenantAliases(rawTenantId));
        params.put("tenantId", tenantId);
        params.put("sourceSystem", sourceSystem);
        params.put("originSourceSystem", firstNonBlank(normalize(query.getOriginSourceSystem()), sourceSystem));
        params.put("hasTargetSystem", hasTargetSystem);
        params.put("targetSystem", firstNonBlank(targetSystem, "__ANY_TARGET_SYSTEM__"));
        params.put("eventStage", firstNonBlank(normalize(query.getEventStage()), "EXTERNAL"));
        params.put("hasEventType", hasEventType);
        params.put("eventType", firstNonBlank(eventType, "__ANY_EVENT_TYPE__"));
        params.put("hasObjectType", hasObjectType);
        params.put("objectType", firstNonBlank(objectType, "__ANY_OBJECT_TYPE__"));
        params.put("hasErrorCode", hasErrorCode);
        params.put("errorCode", firstNonBlank(errorCode, "__ANY_ERROR_CODE__"));
        params.put("hasRequestedSkill", hasRequestedSkill);
        params.put("requestedSkill", firstNonBlank(requestedSkill, "__ANY_REQUESTED_SKILL__"));
        try {
            log.info("flow_rule_runtime_repository_lookup_started tenantId={} normalizedTenantId={} sourceSystem={} originSourceSystem={} eventStage={} targetSystem={} objectType={} eventType={} errorCode={} requestedSkill={} hasRequestedSkill={}",
                    rawTenantId, tenantId, sourceSystem, params.get("originSourceSystem"), params.get("eventStage"), targetSystem, objectType, eventType, errorCode, requestedSkill, hasRequestedSkill);
            Optional<FlowRuleRuntimeMatch> match = Optional.ofNullable(jdbc.queryForObject(SQL, new MapSqlParameterSource(params), MATCH_ROW_MAPPER));
            match.ifPresent(value -> log.info("flow_rule_runtime_repository_matched tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} matchedFlowId={} matchedRuleId={} targetPoolId={} defaultPool={} sourceDefaultPool={}",
                    rawTenantId, sourceSystem, params.get("eventStage"), objectType, eventType, errorCode,
                    value.getFlowId(), value.getRuleId(), value.getTargetPoolId(), value.getDefaultPoolId(), value.isSourceDefaultPool()));
            return match;
        } catch (EmptyResultDataAccessException ex) {
            Map<String, Object> diagnostics = noMatchDiagnostics(params);
            log.warn("flow_rule_runtime_repository_no_match tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requestedSkill={} reason=NO_ACTIVE_FLOW_RULE diagnostics={}",
                    rawTenantId, sourceSystem, params.get("eventStage"), objectType, eventType, errorCode, requestedSkill, diagnostics);
            return Optional.empty();
        } catch (DataAccessException ex) {
            log.error("flow_rule_runtime_repository_sql_failed tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requestedSkill={} exception={} message={}",
                    rawTenantId, sourceSystem, params.get("eventStage"), objectType, eventType, errorCode, requestedSkill,
                    ex.getClass().getSimpleName(), safeMessage(ex), ex);
            throw ex;
        }
    }

    private static final String SQL = """
            with source_flows as (
                select
                    f.tenant_id,
                    f.flow_id,
                    f.flow_code,
                    f.source_system,
                    f.default_pool_id,
                    dp.pool_code as default_pool_code,
                    coalesce(dp.selection_strategy, 'LOWEST_LOAD') as default_selection_strategy,
                    f.default_routing_strategy,
                    f.updated_at
                from dispatch_flows f
                left join agent_pools dp
                  on dp.tenant_id = f.tenant_id
                 and dp.pool_id = f.default_pool_id
                where upper(f.tenant_id) in (:tenantIds)
                  and upper(coalesce(f.status, 'DRAFT')) in ('ACTIVE','ENABLED')
                  and upper(coalesce(f.flow_type, 'SOURCE_FLOW')) = 'SOURCE_FLOW'
                  and upper(f.source_system) in (:sourceSystem, '*')
            ), candidate_rules as (
                select
                    p.tenant_id,
                    p.flow_id,
                    f.flow_code,
                    p.policy_id as rule_id,
                    p.policy_code as rule_code,
                    coalesce(nullif(upper(p.rule_scope), ''),
                             case upper(coalesce(nullif(p.event_stage, ''), :eventStage))
                                  when 'A2A' then 'A2A_DISPATCH'
                                  when 'RESULT' then 'RESULT_CALLBACK'
                                  when 'ISSUE' then 'ISSUE_TRACKING'
                                  else 'EXTERNAL_INTAKE'
                             end) as rule_scope,
                    coalesce(nullif(upper(p.event_stage), ''), :eventStage) as event_stage,
                    upper(coalesce(nullif(p.source_system, ''), nullif(p.origin_source_system, ''), f.source_system)) as source_system,
                    upper(p.origin_source_system) as origin_source_system,
                    upper(p.target_system) as target_system,
                    upper(p.event_type) as event_type,
                    upper(p.object_type) as object_type,
                    upper(p.error_code) as error_code,
                    upper(p.requested_skill) as policy_requested_skill,
                    upper(p.handoff_mode) as handoff_mode,
                    upper(coalesce(p.capability_requirement_mode, 'NONE')) as capability_requirement_mode,
                    upper(p.required_operation) as required_operation,
                    upper(coalesce(p.side_effect_level, 'NONE')) as side_effect_level,
                    upper(coalesce(p.candidate_pool_mode, 'EXPLICIT_FLOW_AGENTS')) as candidate_pool_mode,
                    upper(coalesce(p.routing_strategy, f.default_routing_strategy, 'WEIGHTED_SCORE')) as routing_strategy,
                    coalesce(p.explicit_action_authorization_required, false) as explicit_action_authorization_required,
                    coalesce(p.requirement_model_version, 1) as requirement_model_version,
                    coalesce(string_agg(distinct upper(frs.skill_code), ',') filter (where frs.skill_code is not null and btrim(frs.skill_code) <> ''), '') as required_skills,
                    coalesce(p.target_pool_id, f.default_pool_id) as target_pool_id,
                    coalesce(p.target_pool_code, tp.pool_code, f.default_pool_code) as target_pool_code,
                    f.default_pool_id,
                    coalesce(tp.selection_strategy, f.default_selection_strategy, 'LOWEST_LOAD') as selection_strategy,
                    false as source_default_pool,
                    case when upper(p.tenant_id) = :tenantId then 1000 else 0 end
                    + case when upper(coalesce(nullif(p.event_stage, ''), :eventStage)) = :eventStage then 120 else 0 end
                    + case when upper(coalesce(nullif(p.event_type, ''), '*')) = coalesce(:eventType, '*') then 100 when coalesce(p.event_type, '*') = '*' then 10 else 0 end
                    + case when upper(coalesce(nullif(p.object_type, ''), '*')) = coalesce(:objectType, '*') then 60 when coalesce(p.object_type, '*') = '*' then 5 else 0 end
                    + case when upper(coalesce(nullif(p.error_code, ''), '*')) = coalesce(:errorCode, '*') then 60 when coalesce(p.error_code, '*') = '*' then 5 else 0 end
                    - least(coalesce(p.priority, 100), 1000) as match_score,
                    max(p.updated_at) as updated_at
                from dispatch_policies p
                join source_flows f
                  on f.tenant_id = p.tenant_id
                 and f.flow_id = p.flow_id
                left join agent_pools tp
                  on tp.tenant_id = p.tenant_id
                 and tp.pool_id = coalesce(p.target_pool_id, f.default_pool_id)
                left join flow_required_capabilities frs
                  on frs.tenant_id = p.tenant_id
                 and frs.flow_id = p.flow_id
                 and (frs.rule_id = p.policy_id or frs.rule_id is null)
                 and upper(coalesce(frs.event_stage, p.event_stage, :eventStage)) = upper(coalesce(p.event_stage, :eventStage))
                 and coalesce(frs.required, true) = true
                where upper(p.tenant_id) in (:tenantIds)
                  and p.flow_id is not null
                  and upper(coalesce(p.status, 'DRAFT')) in ('ACTIVE','ENABLED')
                  and upper(coalesce(nullif(p.source_system, ''), nullif(p.origin_source_system, ''), f.source_system)) in (:sourceSystem, '*')
                  and upper(coalesce(nullif(p.event_stage, ''), :eventStage)) in (:eventStage, '*')
                  and (:hasTargetSystem = false or p.target_system is null or upper(p.target_system) in (:targetSystem, '*'))
                  and (:hasEventType = false or p.event_type is null or upper(p.event_type) in (:eventType, '*'))
                  and (:hasObjectType = false or p.object_type is null or upper(p.object_type) in (:objectType, '*'))
                  and (:hasErrorCode = false or p.error_code is null or upper(p.error_code) in (:errorCode, '*'))
                group by p.tenant_id, p.flow_id, f.flow_code, p.policy_id, p.policy_code, p.rule_scope,
                         p.event_stage, p.source_system, p.origin_source_system, f.source_system,
                         p.target_system, p.event_type, p.object_type, p.error_code, p.requested_skill, p.handoff_mode,
                         p.capability_requirement_mode, p.required_operation, p.side_effect_level,
                         p.candidate_pool_mode, p.routing_strategy, f.default_routing_strategy,
                         p.explicit_action_authorization_required, p.requirement_model_version,
                         p.target_pool_id, p.target_pool_code, tp.pool_code, tp.selection_strategy,
                         f.default_pool_id, f.default_pool_code, f.default_selection_strategy, p.priority
            ), source_default as (
                select
                    f.tenant_id,
                    f.flow_id,
                    f.flow_code,
                    'SOURCE_DEFAULT' as rule_id,
                    'SOURCE_DEFAULT' as rule_code,
                    'SOURCE_DEFAULT' as rule_scope,
                    :eventStage as event_stage,
                    upper(f.source_system) as source_system,
                    upper(:originSourceSystem) as origin_source_system,
                    null as target_system,
                    '*' as event_type,
                    '*' as object_type,
                    '*' as error_code,
                    null as policy_requested_skill,
                    'DIRECT_ASSIGN' as handoff_mode,
                    'NONE' as capability_requirement_mode,
                    null as required_operation,
                    'NONE' as side_effect_level,
                    'EXPLICIT_FLOW_AGENTS' as candidate_pool_mode,
                    upper(coalesce(f.default_routing_strategy, 'WEIGHTED_SCORE')) as routing_strategy,
                    false as explicit_action_authorization_required,
                    32 as requirement_model_version,
                    '' as required_skills,
                    f.default_pool_id as target_pool_id,
                    f.default_pool_code as target_pool_code,
                    f.default_pool_id,
                    f.default_selection_strategy as selection_strategy,
                    true as source_default_pool,
                    1 as match_score,
                    f.updated_at
                from source_flows f
            ), candidate_plans as (
                select * from candidate_rules
                union all
                select * from source_default
            )
            select * from candidate_plans
            order by match_score desc, updated_at desc nulls last, rule_id asc
            limit 1
            """;

    private static final String NO_MATCH_DIAGNOSTIC_SQL = """
            with rules as (
                select
                    p.tenant_id,
                    p.flow_id,
                    p.policy_id,
                    upper(coalesce(p.status, 'DRAFT')) as policy_status,
                    upper(coalesce(f.status, 'DRAFT')) as flow_status,
                    upper(coalesce(nullif(p.source_system, ''), nullif(p.origin_source_system, ''), f.source_system)) as source_system,
                    upper(coalesce(nullif(p.event_stage, ''), :eventStage)) as event_stage,
                    upper(coalesce(nullif(p.target_system, ''), '*')) as target_system,
                    upper(coalesce(nullif(p.event_type, ''), '*')) as event_type,
                    upper(coalesce(nullif(p.object_type, ''), '*')) as object_type,
                    upper(coalesce(nullif(p.error_code, ''), '*')) as error_code,
                    upper(p.requested_skill) as requested_skill,
                    upper(coalesce(p.capability_requirement_mode, 'NONE')) as capability_requirement_mode,
                    exists (
                        select 1 from flow_required_capabilities x
                        where x.tenant_id = p.tenant_id
                          and x.flow_id = p.flow_id
                          and (x.rule_id = p.policy_id or x.rule_id is null)
                          and coalesce(x.required, true) = true
                          and x.skill_code is not null
                          and btrim(x.skill_code) <> ''
                    ) as has_required_skill,
                    exists (
                        select 1 from flow_required_capabilities x
                        where x.tenant_id = p.tenant_id
                          and x.flow_id = p.flow_id
                          and (x.rule_id = p.policy_id or x.rule_id is null)
                          and coalesce(x.required, true) = true
                          and upper(x.skill_code) = :requestedSkill
                    ) as has_requested_skill_row
                from dispatch_policies p
                join dispatch_flows f
                  on f.tenant_id = p.tenant_id
                 and f.flow_id = p.flow_id
                where upper(p.tenant_id) in (:tenantIds)
                  and p.flow_id is not null
            ), flags as (
                select *,
                    policy_status in ('ACTIVE','ENABLED') and flow_status in ('ACTIVE','ENABLED') as active_ok,
                    source_system in (:sourceSystem, '*') as source_ok,
                    event_stage in (:eventStage, '*') as stage_ok,
                    (:hasTargetSystem = false or target_system in (:targetSystem, '*')) as target_ok,
                    (:hasEventType = false or event_type in (:eventType, '*')) as event_type_ok,
                    (:hasObjectType = false or object_type in (:objectType, '*')) as object_type_ok,
                    (:hasErrorCode = false or error_code in (:errorCode, '*')) as error_code_ok,
                    (:hasRequestedSkill = false or requested_skill is null or requested_skill in (:requestedSkill, '*') or has_requested_skill_row) as requested_skill_ok,
                    (capability_requirement_mode in ('SOURCE_DEFAULT', 'NONE')
                     or coalesce(nullif(requested_skill, ''), case when has_required_skill then '__REQUIRED_SKILL__' else null end) is not null) as skill_resolved_ok
                from rules
            )
            select
                count(*)::bigint as total_flow_rules,
                count(*) filter (where active_ok)::bigint as active_flow_rules,
                count(*) filter (where active_ok and source_ok)::bigint as source_matched_rules,
                count(*) filter (where active_ok and source_ok and stage_ok)::bigint as stage_matched_rules,
                count(*) filter (where active_ok and source_ok and stage_ok and target_ok)::bigint as target_matched_rules,
                count(*) filter (where active_ok and source_ok and stage_ok and target_ok and event_type_ok)::bigint as event_type_matched_rules,
                count(*) filter (where active_ok and source_ok and stage_ok and target_ok and event_type_ok and object_type_ok)::bigint as object_type_matched_rules,
                count(*) filter (where active_ok and source_ok and stage_ok and target_ok and event_type_ok and object_type_ok and error_code_ok)::bigint as error_code_matched_rules,
                count(*) filter (where active_ok and source_ok and stage_ok and target_ok and event_type_ok and object_type_ok and error_code_ok and requested_skill_ok)::bigint as requested_skill_matched_rules,
                count(*) filter (where active_ok and source_ok and stage_ok and target_ok and event_type_ok and object_type_ok and error_code_ok and requested_skill_ok and skill_resolved_ok)::bigint as skill_resolved_rules
            from flags
            """;

    private Map<String, Object> noMatchDiagnostics(Map<String, Object> params) {
        try {
            Map<String, Object> diagnostics = jdbc.queryForMap(NO_MATCH_DIAGNOSTIC_SQL, new MapSqlParameterSource(params));
            return diagnostics == null ? Map.of("diagnostics", "EMPTY") : diagnostics;
        } catch (RuntimeException ex) {
            log.warn("flow_rule_runtime_repository_no_match_diagnostics_failed tenantId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} requestedSkill={} exception={} message={}",
                    params.get("tenantId"), params.get("sourceSystem"), params.get("eventStage"), params.get("eventType"), params.get("objectType"), params.get("errorCode"), params.get("requestedSkill"),
                    ex.getClass().getSimpleName(), safeMessage(ex));
            return Map.of("diagnostics", "FAILED", "exception", ex.getClass().getSimpleName(), "message", safeMessage(ex));
        }
    }

    private static final RowMapper<FlowRuleRuntimeMatch> MATCH_ROW_MAPPER = new RowMapper<>() {
        @Override
        public FlowRuleRuntimeMatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            FlowRuleRuntimeMatch match = new FlowRuleRuntimeMatch();
            match.setTenantId(rs.getString("tenant_id"));
            match.setFlowId(rs.getString("flow_id"));
            match.setFlowCode(rs.getString("flow_code"));
            match.setRuleId(rs.getString("rule_id"));
            match.setRuleCode(rs.getString("rule_code"));
            match.setRuleScope(rs.getString("rule_scope"));
            match.setEventStage(rs.getString("event_stage"));
            match.setSourceSystem(rs.getString("source_system"));
            match.setOriginSourceSystem(rs.getString("origin_source_system"));
            match.setTargetSystem(rs.getString("target_system"));
            match.setEventType(rs.getString("event_type"));
            match.setObjectType(rs.getString("object_type"));
            match.setErrorCode(rs.getString("error_code"));
            String skills = rs.getString("required_skills");
            List<String> requiredSkills = splitSkills(skills);
            match.setRequiredSkills(requiredSkills);
            match.setRequestedSkill(firstNonBlankStatic(rs.getString("policy_requested_skill"), requiredSkills.isEmpty() ? null : requiredSkills.get(0)));
            match.setHandoffMode(rs.getString("handoff_mode"));
            match.setCapabilityRequirementMode(rs.getString("capability_requirement_mode"));
            match.setRequiredOperation(rs.getString("required_operation"));
            match.setSideEffectLevel(rs.getString("side_effect_level"));
            match.setCandidatePoolMode(rs.getString("candidate_pool_mode"));
            match.setRoutingStrategy(rs.getString("routing_strategy"));
            match.setTargetPoolId(rs.getString("target_pool_id"));
            match.setTargetPoolCode(rs.getString("target_pool_code"));
            match.setDefaultPoolId(rs.getString("default_pool_id"));
            match.setSelectionStrategy(rs.getString("selection_strategy"));
            match.setSourceDefaultPool(rs.getBoolean("source_default_pool"));
            match.setExplicitActionAuthorizationRequired(rs.getBoolean("explicit_action_authorization_required"));
            match.setRequirementModelVersion(rs.getInt("requirement_model_version"));
            match.setMatchReason((match.isSourceDefaultPool() ? "Source Flow default Pool selected" : "Persisted Flow-owned Dispatch Rule matched")
                    + ": flowId=" + match.getFlowId()
                    + ", ruleId=" + match.getRuleId()
                    + ", targetPoolId=" + match.getTargetPoolId());
            return match;
        }
    };

    private static List<String> tenantAliases(String rawTenantId) {
        if (blank(rawTenantId)) return List.of();
        return Arrays.stream(new String[] { rawTenantId, rawTenantId.toUpperCase(Locale.ROOT), rawTenantId.toLowerCase(Locale.ROOT) })
                .map(value -> value == null ? null : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    private static String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) return "-";
        return ex.getMessage().replace('\n', ' ').replace('\r', ' ');
    }

    private static List<String> splitSkills(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(JdbcFlowRuleRoutingRepository::normalize)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    private static String firstNonBlankStatic(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " is required");
        return value;
    }

    private static String firstNonBlank(String... values) {
        return firstNonBlankStatic(values);
    }

    private static String normalize(String value) {
        if (blank(value)) return null;
        String normalized = value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return normalized.startsWith("NO_") ? null : normalized;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
