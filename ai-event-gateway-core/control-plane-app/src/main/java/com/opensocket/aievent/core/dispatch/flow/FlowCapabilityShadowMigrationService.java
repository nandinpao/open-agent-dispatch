package com.opensocket.aievent.core.dispatch.flow;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Stage 3 / A0-R3 Flow -> Capability migration shadow evaluator.
 *
 * <p>This service is deliberately off the production Dispatch path. It never calls
 * TaskAssignmentService, DispatchRequestService, ProviderRoutingService runtime
 * evaluation, ExecutionAdapterService, Netty, A2A or MCP. Existing Flow/Rule and
 * flow_agent_assignments remain the only execution authority during Stage 3.</p>
 */
@Service
public class FlowCapabilityShadowMigrationService {
    private static final String EVALUATOR_VERSION = "STAGE3_A0_R3_V1";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final Set<String> STAGE3_STATES = Set.of("LEGACY_DIRECT", "SHADOW_EVALUATION", "CAPABILITY_READY", "READY_FOR_COMPAT_CUTOVER", "ROLLED_BACK");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public FlowCapabilityShadowMigrationService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public MigrationState state(String tenantId, String flowId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        ensureFlow(tenant, flowId);
        return loadState(tenant, flowId);
    }

    @Transactional
    public MigrationState changeState(String tenantId, String flowId, StateChange request, String actor) {
        String tenant = requireTenant(tenantId);
        String flow = requireNonBlank(flowId, "flowId");
        bindDatabaseTenantContext(tenant);
        ensureFlow(tenant, flow);
        if (request == null) throw new IllegalArgumentException("State change request is required");
        String target = normalizeState(request.migrationState());
        MigrationState current = loadState(tenant, flow);
        validateTransition(current.migrationState(), target);
        boolean shadowEnabled = Set.of("SHADOW_EVALUATION", "CAPABILITY_READY", "READY_FOR_COMPAT_CUTOVER").contains(target);
        String reason = trimToNull(request.reason());
        if (("CAPABILITY_READY".equals(target) || "READY_FOR_COMPAT_CUTOVER".equals(target) || "ROLLED_BACK".equals(target)) && reason == null) {
            throw new IllegalArgumentException("reason is required for " + target);
        }
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                update flow_capability_migration_states
                   set migration_state=:state,
                       authoritative_mode='LEGACY_FLOW_DIRECT',
                       shadow_enabled=:shadowEnabled,
                       shadow_started_at=case when :shadowEnabled then coalesce(shadow_started_at,:now) else shadow_started_at end,
                       state_reason=:reason,
                       version=version+1,
                       updated_by=:actor,
                       updated_at=:now
                 where tenant_id=:tenantId and flow_id=:flowId
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenant).addValue("flowId", flow)
                .addValue("state", target).addValue("shadowEnabled", shadowEnabled)
                .addValue("reason", reason).addValue("actor", actor(actor)).addValue("now", now));
        return loadState(tenant, flow);
    }

    @Transactional
    public ShadowEvaluation evaluateTask(String tenantId, String flowId, String taskId, String actor) {
        String tenant = requireTenant(tenantId);
        String flow = requireNonBlank(flowId, "flowId");
        String task = requireNonBlank(taskId, "taskId");
        bindDatabaseTenantContext(tenant);
        MigrationState migration = loadState(tenant, flow);
        if (!migration.shadowEnabled() || !"SHADOW_EVALUATION".equals(migration.migrationState())) {
            throw new IllegalStateException("Flow must be in SHADOW_EVALUATION before shadow evidence can be generated");
        }

        TaskShadowInput input = loadTask(tenant, flow, task);
        List<String> legacySkills = legacyRequiredSkills(tenant, input);
        Resolution resolution = resolveCapabilities(tenant, legacySkills);
        List<String> legacyCandidates = legacyCandidates(tenant, flow, input.eventStage());
        String legacySelected = latestSelectedAgent(tenant, task);
        ShadowCandidateSet shadow = resolution.capabilities().isEmpty()
                ? new ShadowCandidateSet(List.of(), List.of())
                : shadowCandidates(tenant, resolution.capabilities());

        Comparison comparison = compare(legacyCandidates, legacySelected, legacySkills, resolution, shadow.agentIds());
        OffsetDateTime now = OffsetDateTime.now();
        String evaluationId = "flow-cap-shadow-" + UUID.randomUUID();

        jdbc.update("""
                insert into flow_capability_shadow_evaluations(
                  tenant_id,evaluation_id,task_id,task_version,flow_id,rule_id,legacy_selected_agent_id,
                  legacy_candidate_agents_json,legacy_required_skills_json,resolved_capabilities_json,unresolved_skills_json,
                  shadow_candidate_agents_json,shadow_candidate_bindings_json,comparison_result,reason_codes_json,
                  authority_mode,side_effect_allowed,evaluator_version,evaluated_by,evaluated_at)
                values(:tenantId,:evaluationId,:taskId,:taskVersion,:flowId,:ruleId,:legacySelected,
                  cast(:legacyAgents as jsonb),cast(:legacySkills as jsonb),cast(:capabilities as jsonb),cast(:unresolved as jsonb),
                  cast(:shadowAgents as jsonb),cast(:shadowBindings as jsonb),:comparison,cast(:reasons as jsonb),
                  'SHADOW_ONLY',false,:evaluatorVersion,:actor,:evaluatedAt)
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenant).addValue("evaluationId", evaluationId)
                .addValue("taskId", task).addValue("taskVersion", input.taskVersion())
                .addValue("flowId", flow).addValue("ruleId", input.ruleId()).addValue("legacySelected", legacySelected)
                .addValue("legacyAgents", writeJson(legacyCandidates)).addValue("legacySkills", writeJson(legacySkills))
                .addValue("capabilities", writeJson(resolution.capabilities())).addValue("unresolved", writeJson(resolution.unresolvedSkills()))
                .addValue("shadowAgents", writeJson(shadow.agentIds())).addValue("shadowBindings", writeJson(shadow.bindings()))
                .addValue("comparison", comparison.result()).addValue("reasons", writeJson(comparison.reasonCodes()))
                .addValue("evaluatorVersion", EVALUATOR_VERSION).addValue("actor", actor(actor)).addValue("evaluatedAt", now));

        jdbc.update("""
                update flow_capability_migration_states
                   set last_evaluated_at=:now,updated_at=:now,updated_by=:actor
                 where tenant_id=:tenantId and flow_id=:flowId
                """, new MapSqlParameterSource("tenantId", tenant).addValue("flowId", flow)
                .addValue("now", now).addValue("actor", actor(actor)));

        return latestEvaluationForTask(tenant, task, input.taskVersion());
    }

    @Transactional(readOnly = true)
    public List<ShadowEvaluation> evaluations(String tenantId, String flowId, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        return jdbc.query("""
                select evaluation_id,task_id,task_version,flow_id,rule_id,legacy_selected_agent_id,
                       legacy_candidate_agents_json,legacy_required_skills_json,resolved_capabilities_json,unresolved_skills_json,
                       shadow_candidate_agents_json,shadow_candidate_bindings_json,comparison_result,reason_codes_json,
                       authority_mode,side_effect_allowed,evaluator_version,evaluated_by,evaluated_at
                  from flow_capability_shadow_evaluations
                 where tenant_id=:tenantId and flow_id=:flowId
                 order by evaluated_at desc,evaluation_id desc
                 limit :limit
                """, new MapSqlParameterSource("tenantId", tenant).addValue("flowId", requireNonBlank(flowId, "flowId"))
                .addValue("limit", Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000))),
                (rs, rowNum) -> mapEvaluation(rs));
    }

    @Transactional(readOnly = true)
    public MigrationReadiness readiness(String tenantId, String flowId, int minimumSampleSize) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MigrationState state = loadState(tenant, flowId);
        Map<String, Object> row = jdbc.queryForMap("""
                select sample_size,unmapped_capability_count,no_shadow_provider_count,
                       selected_not_eligible_count,legacy_preserved_count,last_evaluated_at
                  from flow_capability_migration_readiness_v53
                 where tenant_id=:tenantId and flow_id=:flowId
                """, new MapSqlParameterSource("tenantId", tenant).addValue("flowId", requireNonBlank(flowId, "flowId")));
        long sample = number(row.get("sample_size"));
        long unmapped = number(row.get("unmapped_capability_count"));
        long noProvider = number(row.get("no_shadow_provider_count"));
        long selectedNotEligible = number(row.get("selected_not_eligible_count"));
        long preserved = number(row.get("legacy_preserved_count"));
        int minimum = Math.max(1, Math.min(minimumSampleSize <= 0 ? 10 : minimumSampleSize, 10000));
        boolean ready = sample >= minimum && unmapped == 0 && noProvider == 0 && selectedNotEligible == 0 && preserved == sample;
        List<String> blockers = new ArrayList<>();
        if (sample < minimum) blockers.add("INSUFFICIENT_SHADOW_SAMPLE");
        if (unmapped > 0) blockers.add("UNMAPPED_CAPABILITY");
        if (noProvider > 0) blockers.add("NO_SHADOW_PROVIDER");
        if (selectedNotEligible > 0) blockers.add("LEGACY_SELECTED_NOT_ELIGIBLE");
        if (preserved < sample) blockers.add("LEGACY_CANDIDATE_SET_NOT_PRESERVED");
        return new MigrationReadiness(state, sample, minimum, unmapped, noProvider, selectedNotEligible, preserved,
                ready ? "CAPABILITY_READY" : "SHADOW_EVALUATION", List.copyOf(blockers), row.get("last_evaluated_at"));
    }

    private TaskShadowInput loadTask(String tenant, String flow, String taskId) {
        try {
            return jdbc.queryForObject("""
                    select task_id,coalesce(version,0) as task_version,matched_flow_id,matched_rule_id,
                           coalesce(event_stage,'EXTERNAL') as event_stage,requested_skill,required_capabilities_json
                      from tasks
                     where tenant_id=:tenantId and task_id=:taskId and matched_flow_id=:flowId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("taskId", taskId).addValue("flowId", flow),
                    (rs, rowNum) -> new TaskShadowInput(rs.getString("task_id"), rs.getLong("task_version"), rs.getString("matched_flow_id"),
                            rs.getString("matched_rule_id"), rs.getString("event_stage"), rs.getString("requested_skill"),
                            parseStringList(rs.getString("required_capabilities_json"))));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Task is not associated with the requested Dispatch Flow: " + taskId);
        }
    }

    private List<String> legacyRequiredSkills(String tenant, TaskShadowInput input) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (!blank(input.requestedSkill())) values.add(input.requestedSkill().trim());
        if (input.taskCapabilities() != null) input.taskCapabilities().stream().filter(v -> !blank(v)).map(String::trim).forEach(values::add);
        List<String> flowSkills = jdbc.queryForList("""
                select distinct skill_code
                  from flow_required_capabilities
                 where tenant_id=:tenantId and flow_id=:flowId and coalesce(required,true)=true
                   and (rule_id is null or rule_id=:ruleId)
                   and skill_code is not null and btrim(skill_code)<>''
                 order by skill_code
                """, new MapSqlParameterSource("tenantId", tenant).addValue("flowId", input.flowId()).addValue("ruleId", input.ruleId()), String.class);
        flowSkills.stream().filter(v -> !blank(v)).map(String::trim).forEach(values::add);
        return List.copyOf(values);
    }

    private Resolution resolveCapabilities(String tenant, List<String> skills) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        List<String> unresolved = new ArrayList<>();
        for (String raw : skills) {
            String skill = raw == null ? "" : raw.trim();
            if (skill.isEmpty()) continue;
            List<String> matches = jdbc.queryForList("""
                    select capability_code from (
                      select c.capability_code,1 as rank
                        from capability_definitions c
                       where c.tenant_id=:tenantId and c.status='ACTIVE' and lower(c.capability_code)=lower(:skill)
                      union all
                      select a.capability_code,2 as rank
                        from skill_capability_aliases a
                        join capability_definitions c on c.tenant_id=a.tenant_id and c.capability_code=a.capability_code and c.status='ACTIVE'
                       where a.tenant_id=:tenantId and a.status='ACTIVE' and upper(a.legacy_skill_code)=upper(:skill)
                    ) x order by rank,capability_code limit 1
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("skill", skill), String.class);
            if (matches.isEmpty()) unresolved.add(skill); else capabilities.add(matches.getFirst());
        }
        return new Resolution(List.copyOf(capabilities), List.copyOf(unresolved));
    }

    private List<String> legacyCandidates(String tenant, String flow, String eventStage) {
        return jdbc.queryForList("""
                select distinct agent_id
                  from flow_agent_assignments
                 where tenant_id=:tenantId and flow_id=:flowId
                   and upper(coalesce(event_stage,'EXTERNAL')) in (upper(:eventStage),'*')
                   and upper(coalesce(assignment_status,'DRAFT')) in ('ACTIVE','ENABLED')
                   and upper(coalesce(approval_status,'PENDING')) in ('APPROVED','ACTIVE')
                 order by agent_id
                """, new MapSqlParameterSource("tenantId", tenant).addValue("flowId", flow).addValue("eventStage", eventStage), String.class);
    }

    private String latestSelectedAgent(String tenant, String taskId) {
        List<String> values = jdbc.queryForList("""
                select agent_id from task_assignments
                 where tenant_id=:tenantId and task_id=:taskId and agent_id is not null
                 order by created_at desc,assignment_id desc limit 1
                """, new MapSqlParameterSource("tenantId", tenant).addValue("taskId", taskId), String.class);
        return values.isEmpty() ? null : values.getFirst();
    }

    private ShadowCandidateSet shadowCandidates(String tenant, List<String> capabilities) {
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant)
                .addValue("capabilities", capabilities).addValue("requiredCount", capabilities.size());
        List<String> agentIds = jdbc.queryForList("""
                select l.agent_id
                  from capability_bindings b
                  join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
                  join managed_agent_provider_links l on l.tenant_id=p.tenant_id and l.provider_id=p.provider_id and l.status='ACTIVE'
                 where b.tenant_id=:tenantId
                   and b.capability_code in (:capabilities)
                   and b.trust_status='APPROVED'
                   and p.catalog_status='REGISTERED'
                   and p.provider_type='MANAGED_AGENT'
                 group by l.agent_id
                having count(distinct b.capability_code)=:requiredCount
                 order by l.agent_id
                """, params, String.class);
        List<Map<String, Object>> bindings = jdbc.query("""
                select b.binding_id,b.capability_code,b.provider_id,l.agent_id
                  from capability_bindings b
                  join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
                  join managed_agent_provider_links l on l.tenant_id=p.tenant_id and l.provider_id=p.provider_id and l.status='ACTIVE'
                 where b.tenant_id=:tenantId
                   and b.capability_code in (:capabilities)
                   and b.trust_status='APPROVED'
                   and p.catalog_status='REGISTERED'
                   and p.provider_type='MANAGED_AGENT'
                 order by l.agent_id,b.capability_code,b.binding_id
                """, params, (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bindingId", rs.getString("binding_id"));
                    row.put("capabilityCode", rs.getString("capability_code"));
                    row.put("providerId", rs.getString("provider_id"));
                    row.put("agentId", rs.getString("agent_id"));
                    return Map.copyOf(row);
                });
        return new ShadowCandidateSet(List.copyOf(agentIds), List.copyOf(bindings));
    }

    static Comparison compare(List<String> legacyCandidates, String legacySelected, List<String> legacySkills,
                              Resolution resolution, List<String> shadowCandidates) {
        List<String> reasons = new ArrayList<>();
        if (legacySkills == null || legacySkills.isEmpty()) return new Comparison("NO_REQUIRED_CAPABILITY", List.of("NO_REQUIRED_CAPABILITY"));
        if (resolution != null && !resolution.unresolvedSkills().isEmpty()) return new Comparison("UNMAPPED_CAPABILITY", List.of("UNMAPPED_CAPABILITY"));
        if (resolution == null || resolution.capabilities().isEmpty()) return new Comparison("NO_REQUIRED_CAPABILITY", List.of("NO_REQUIRED_CAPABILITY"));
        if (legacyCandidates == null || legacyCandidates.isEmpty()) return new Comparison("NO_LEGACY_CANDIDATE", List.of("NO_LEGACY_CANDIDATE"));
        if (shadowCandidates == null || shadowCandidates.isEmpty()) return new Comparison("NO_SHADOW_PROVIDER", List.of("NO_SHADOW_PROVIDER"));
        Set<String> legacy = new LinkedHashSet<>(legacyCandidates);
        Set<String> shadow = new LinkedHashSet<>(shadowCandidates);
        if (!blank(legacySelected) && !shadow.contains(legacySelected)) {
            return new Comparison("LEGACY_SELECTED_NOT_ELIGIBLE", List.of("LEGACY_SELECTED_NOT_CAPABILITY_ELIGIBLE"));
        }
        if (legacy.equals(shadow)) return new Comparison("EQUIVALENT_CANDIDATE_SET", List.of("LEGACY_CANDIDATE_SET_PRESERVED"));
        if (shadow.containsAll(legacy)) {
            reasons.add("LEGACY_CANDIDATE_SET_PRESERVED");
            reasons.add("SHADOW_ADDS_CAPABILITY_ELIGIBLE_CANDIDATES");
            return new Comparison("SHADOW_EXPANDS_CANDIDATES", List.copyOf(reasons));
        }
        if (legacy.containsAll(shadow)) return new Comparison("SHADOW_REDUCES_CANDIDATES", List.of("LEGACY_CANDIDATES_MISSING_CAPABILITY_BINDING"));
        return new Comparison("DIFFERENT_CANDIDATE_SET", List.of("LEGACY_AND_CAPABILITY_CANDIDATE_SETS_DIFFER"));
    }

    private ShadowEvaluation latestEvaluationForTask(String tenant, String taskId, long taskVersion) {
        return jdbc.queryForObject("""
                select evaluation_id,task_id,task_version,flow_id,rule_id,legacy_selected_agent_id,
                       legacy_candidate_agents_json,legacy_required_skills_json,resolved_capabilities_json,unresolved_skills_json,
                       shadow_candidate_agents_json,shadow_candidate_bindings_json,comparison_result,reason_codes_json,
                       authority_mode,side_effect_allowed,evaluator_version,evaluated_by,evaluated_at
                  from flow_capability_shadow_evaluations
                 where tenant_id=:tenantId and task_id=:taskId and task_version=:taskVersion and evaluator_version=:version
                 order by evaluated_at desc limit 1
                """, new MapSqlParameterSource("tenantId", tenant).addValue("taskId", taskId)
                .addValue("taskVersion", taskVersion).addValue("version", EVALUATOR_VERSION),
                (rs, rowNum) -> mapEvaluation(rs));
    }

    private ShadowEvaluation mapEvaluation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ShadowEvaluation(rs.getString("evaluation_id"), rs.getString("task_id"), rs.getLong("task_version"),
                rs.getString("flow_id"), rs.getString("rule_id"), rs.getString("legacy_selected_agent_id"),
                parseStringList(rs.getString("legacy_candidate_agents_json")), parseStringList(rs.getString("legacy_required_skills_json")),
                parseStringList(rs.getString("resolved_capabilities_json")), parseStringList(rs.getString("unresolved_skills_json")),
                parseStringList(rs.getString("shadow_candidate_agents_json")), parseObjectList(rs.getString("shadow_candidate_bindings_json")),
                rs.getString("comparison_result"), parseStringList(rs.getString("reason_codes_json")),
                rs.getString("authority_mode"), rs.getBoolean("side_effect_allowed"), rs.getString("evaluator_version"),
                rs.getString("evaluated_by"), rs.getObject("evaluated_at", OffsetDateTime.class));
    }

    private MigrationState loadState(String tenant, String flowId) {
        try {
            return jdbc.queryForObject("""
                    select flow_id,migration_state,authoritative_mode,shadow_enabled,shadow_started_at,last_evaluated_at,
                           state_reason,version,updated_by,updated_at
                      from flow_capability_migration_states
                     where tenant_id=:tenantId and flow_id=:flowId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("flowId", requireNonBlank(flowId, "flowId")),
                    (rs, rowNum) -> new MigrationState(rs.getString("flow_id"), rs.getString("migration_state"),
                            rs.getString("authoritative_mode"), rs.getBoolean("shadow_enabled"),
                            rs.getObject("shadow_started_at", OffsetDateTime.class), rs.getObject("last_evaluated_at", OffsetDateTime.class),
                            rs.getString("state_reason"), rs.getInt("version"), rs.getString("updated_by"),
                            rs.getObject("updated_at", OffsetDateTime.class)));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Flow migration state not found: " + flowId);
        }
    }

    private void ensureFlow(String tenant, String flowId) {
        Integer count = jdbc.queryForObject("select count(*) from dispatch_flows where tenant_id=:tenantId and flow_id=:flowId",
                new MapSqlParameterSource("tenantId", tenant).addValue("flowId", requireNonBlank(flowId, "flowId")), Integer.class);
        if (count == null || count == 0) throw new IllegalArgumentException("Dispatch Flow not found: " + flowId);
    }

    private void validateTransition(String current, String target) {
        if (current.equals(target)) return;
        boolean allowed = switch (current) {
            case "LEGACY_DIRECT" -> "SHADOW_EVALUATION".equals(target);
            case "SHADOW_EVALUATION" -> Set.of("CAPABILITY_READY", "READY_FOR_COMPAT_CUTOVER", "ROLLED_BACK").contains(target);
            case "CAPABILITY_READY", "READY_FOR_COMPAT_CUTOVER" -> Set.of("SHADOW_EVALUATION", "ROLLED_BACK").contains(target);
            case "ROLLED_BACK" -> "SHADOW_EVALUATION".equals(target);
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("Unsupported Stage 3 migration transition: " + current + " -> " + target);
    }

    private String normalizeState(String value) {
        String state = requireNonBlank(value, "migrationState").trim().toUpperCase(Locale.ROOT);
        if (!STAGE3_STATES.contains(state)) throw new IllegalArgumentException("Stage 3 cannot enter migration state: " + state);
        return state;
    }

    private void bindDatabaseTenantContext(String tenantId) {
        IamTenantExecutionContext requestContext = IamTenantContextHolder.current().orElse(null);
        if (requestContext != null && !"INSTANCE".equalsIgnoreCase(requestContext.tenantId()) && !tenantId.equals(requestContext.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for Flow Capability migration");
        }
        String actor = requestContext == null || blank(requestContext.actorId()) ? "flow-capability-shadow-migration" : requestContext.actorId();
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }

    private List<String> parseStringList(String value) {
        if (blank(value)) return List.of();
        try { return List.copyOf(json.readValue(value, STRING_LIST)); }
        catch (Exception ex) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseObjectList(String value) {
        if (blank(value)) return List.of();
        try {
            List<Map<String, Object>> rows = json.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
            return rows == null ? List.of() : List.copyOf(rows);
        } catch (Exception ex) { return List.of(); }
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value == null ? List.of() : value); }
        catch (Exception ex) { throw new IllegalStateException("Unable to serialize Stage 3 shadow evidence", ex); }
    }

    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private static String actor(String ignoredClientValue) {
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        return context == null || blank(context.actorId()) ? "FLOW_CAPABILITY_MIGRATION_SYSTEM" : context.actorId().trim();
    }
    private static String requireTenant(String value) { return requireNonBlank(value, "tenantId"); }
    private static String requireNonBlank(String value, String field) {
        if (blank(value)) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String trimToNull(String value) { return blank(value) ? null : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record StateChange(String migrationState, String reason) {}
    public record MigrationState(String flowId, String migrationState, String authoritativeMode, boolean shadowEnabled,
                                 OffsetDateTime shadowStartedAt, OffsetDateTime lastEvaluatedAt, String stateReason,
                                 int version, String updatedBy, OffsetDateTime updatedAt) {}
    public record ShadowEvaluation(String evaluationId, String taskId, long taskVersion, String flowId, String ruleId,
                                   String legacySelectedAgentId, List<String> legacyCandidateAgentIds,
                                   List<String> legacyRequiredSkills, List<String> resolvedCapabilities,
                                   List<String> unresolvedSkills, List<String> shadowCandidateAgentIds,
                                   List<Map<String, Object>> shadowCandidateBindings, String comparisonResult,
                                   List<String> reasonCodes, String authorityMode, boolean sideEffectAllowed,
                                   String evaluatorVersion, String evaluatedBy, OffsetDateTime evaluatedAt) {}
    public record MigrationReadiness(MigrationState state, long sampleSize, int minimumSampleSize,
                                     long unmappedCapabilityCount, long noShadowProviderCount,
                                     long selectedNotEligibleCount, long legacyPreservedCount,
                                     String recommendedState, List<String> blockers, Object lastEvaluatedAt) {}
    record TaskShadowInput(String taskId, long taskVersion, String flowId, String ruleId, String eventStage,
                           String requestedSkill, List<String> taskCapabilities) {}
    record Resolution(List<String> capabilities, List<String> unresolvedSkills) {}
    record ShadowCandidateSet(List<String> agentIds, List<Map<String, Object>> bindings) {}
    record Comparison(String result, List<String> reasonCodes) {}
}
