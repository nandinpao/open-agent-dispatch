package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;

@Service
public class DispatchFlowReadinessService {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowReadinessService.class);
    private final NamedParameterJdbcTemplate jdbc;

    public DispatchFlowReadinessService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DispatchFlowReadinessResponse dryRun(DispatchFlowReadinessRequest request) {
        DispatchFlowReadinessRequest normalized = normalizeRequest(request);
        DispatchFlowReadinessResponse response = new DispatchFlowReadinessResponse();
        response.setTenantId(normalized.getTenantId());
        response.setSourceSystem(normalized.getSourceSystem());
        response.setOriginSourceSystem(normalized.getOriginSourceSystem());
        response.setTargetSystem(normalized.getTargetSystem());
        response.setEventStage(normalized.getEventStage());
        response.setObjectType(normalized.getObjectType());
        response.setEventType(normalized.getEventType());
        response.setErrorCode(normalized.getErrorCode());
        response.setRequestedSkill(null);
        response.setGeneratedAt(OffsetDateTime.now());

        log.info("dispatch_flow_dry_run_started tenantId={} flowId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requiredCapability={} agentId={}",
                normalized.getTenantId(), normalized.getFlowId(), normalized.getSourceSystem(), normalized.getEventStage(), normalized.getObjectType(),
                normalized.getEventType(), normalized.getErrorCode(), null, normalized.getAgentId());
        Optional<Map<String, Object>> maybeRule = findBestRule(normalized);
        if (maybeRule.isEmpty()) {
            response.setChecks(List.of(
                    check("FLOW_RULE_MATCH", "BLOCKED", "No ACTIVE Flow-owned Dispatch Rule matched this event.", true, "Create or activate a Dispatch Flow Rule for this source/event."),
                    check("FLOW_AGENT_ASSIGNMENT", "SKIPPED", "Agent assignment cannot be checked until a Flow Rule matches.", false, "Assign an Agent after the Flow Rule exists.")
            ));
            response.setStatus("BLOCKED");
            response.setReady(false);
            response.setDispatchable(false);
            response.setFirstBlockingCode("MISSING_FLOW_RULE");
            response.setFirstBlockingReason("No ACTIVE Flow-owned Dispatch Rule matched this event. Runtime would not produce matchedFlowId/matchedRuleId.");
            response.setSummary("Flow Rule dry-run blocked before Agent selection: no matching active Flow-owned rule.");
            response.setDiagnostics(Map.of("mode", "P2_FLOW_RULE_DRY_RUN", "legacyReadinessUsed", false));
            log.warn("dispatch_flow_dry_run_blocked tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requiredCapability={} blockingCode={} reason={}",
                    normalized.getTenantId(), normalized.getSourceSystem(), normalized.getEventStage(), normalized.getObjectType(), normalized.getEventType(),
                    normalized.getErrorCode(), null, response.getFirstBlockingCode(), response.getFirstBlockingReason());
            return response;
        }

        Map<String, Object> rule = maybeRule.get();
        String flowId = string(rule.get("flow_id"));
        String ruleId = string(rule.get("rule_id"));
        List<String> requiredCapabilities = splitSkills(string(rule.get("required_skills")), null);
        String requestedSkill = requiredCapabilities.isEmpty() ? null : requiredCapabilities.get(0);
        String eventStage = firstNonBlank(string(rule.get("event_stage")), normalized.getEventStage());
        response.setTenantId(string(rule.get("tenant_id")));
        response.setFlowId(flowId);
        response.setFlowCode(string(rule.get("flow_code")));
        response.setRuleId(ruleId);
        response.setRuleCode(string(rule.get("rule_code")));
        response.setSourceSystem(firstNonBlank(string(rule.get("source_system")), normalized.getSourceSystem()));
        response.setOriginSourceSystem(string(rule.get("origin_source_system")));
        response.setTargetSystem(string(rule.get("target_system")));
        response.setEventStage(eventStage);
        response.setObjectType(firstNonBlank(string(rule.get("object_type")), normalized.getObjectType()));
        response.setEventType(firstNonBlank(string(rule.get("event_type")), normalized.getEventType()));
        response.setErrorCode(firstNonBlank(string(rule.get("error_code")), normalized.getErrorCode()));
        response.setRequestedSkill(requestedSkill);
        response.setRequiredSkills(requiredCapabilities);

        long requiredCapabilityMatches = countRequiredCapability(response.getTenantId(), flowId, ruleId, eventStage, requestedSkill);
        List<DispatchFlowCandidateAgentView> agents = candidateAgents(response.getTenantId(), flowId, eventStage, requestedSkill, normalized.getAgentId());
        response.setCandidateAgents(agents);
        long approvedAssignments = agents.stream().filter(agent -> Boolean.TRUE.equals(agent.getAssignmentActive()) && Boolean.TRUE.equals(agent.getApprovalReady())).count();
        long readyAssignments = agents.stream().filter(agent -> Boolean.TRUE.equals(agent.getAssignmentActive()) && Boolean.TRUE.equals(agent.getApprovalReady()) && Boolean.TRUE.equals(agent.getReadinessReady())).count();
        long agentsWithRequiredCapability = agents.stream().filter(agent -> Boolean.TRUE.equals(agent.getAssignmentActive()) && Boolean.TRUE.equals(agent.getApprovalReady()) && Boolean.TRUE.equals(agent.getRequestedSkillGranted())).count();
        long dispatchableAgents = agents.stream().filter(agent -> Boolean.TRUE.equals(agent.getDispatchable())).count();
        agents.stream().filter(agent -> Boolean.TRUE.equals(agent.getDispatchable())).findFirst().ifPresent(agent -> response.setSelectedAgentId(agent.getAgentId()));
        if (response.getSelectedAgentId() == null && !agents.isEmpty()) {
            response.setSelectedAgentId(agents.get(0).getAgentId());
        }

        List<DispatchFlowReadinessCheck> checks = new ArrayList<>();
        checks.add(check("FLOW_RULE_MATCH", "PASS", "Matched active Flow-owned rule " + response.getRuleCode() + ".", false, null,
                Map.of("matchedFlowId", flowId, "matchedRuleId", ruleId)));
        checks.add(requiredCapabilities.isEmpty()
                ? check("REQUIRED_CAPABILITY_OPTIONAL", "PASS", "This Flow Rule has no Required Capability; capability checks are optional and will not block dispatch.", false, null)
                : check("FLOW_REQUIRED_CAPABILITY", "PASS", "Required Capability row(s) are defined: " + String.join(", ", requiredCapabilities) + ".", false, null));
        if (!requiredCapabilities.isEmpty()) {
            checks.add(requiredCapabilityMatches > 0
                    ? check("FLOW_REQUIRED_CAPABILITY_ROW", "PASS", "Matching flow_required_capabilities compatibility row exists for Required Capability " + requestedSkill + ".", false, null)
                    : check("FLOW_REQUIRED_CAPABILITY_ROW", "BLOCKED", "No flow_required_capabilities compatibility row matches the Required Capability.", true, "Add the Required Capability to this Dispatch Flow."));
        }
        checks.add(approvedAssignments > 0
                ? check("FLOW_AGENT_ASSIGNMENT", "PASS", approvedAssignments + " approved Flow Agent assignment(s) found.", false, null)
                : check("FLOW_AGENT_ASSIGNMENT", "BLOCKED", "No approved active Agent assignment exists on this Flow/event stage.", true, "Assign and approve at least one Agent on this Dispatch Flow."));
        checks.add(readyAssignments > 0
                ? check("FLOW_AGENT_READINESS", "PASS", readyAssignments + " Flow Agent assignment(s) are marked READY/FLOW_AGENT_READY.", false, null)
                : check("FLOW_AGENT_READINESS", approvedAssignments > 0 ? "BLOCKED" : "SKIPPED", "No approved Flow Agent assignment is marked ready.", approvedAssignments > 0, "Repair Flow Agent readiness or select a ready Agent."));
        checks.add(requiredCapabilities.isEmpty()
                ? check("AGENT_REQUIRED_CAPABILITY", "PASS", "No Required Capability is configured; Agent capability grant is not required.", false, null)
                : agentsWithRequiredCapability > 0
                    ? check("AGENT_REQUIRED_CAPABILITY", "PASS", agentsWithRequiredCapability + " assigned Agent(s) hold the required approved Capability.", false, null)
                    : check("AGENT_REQUIRED_CAPABILITY", "BLOCKED", "No assigned Agent has an approved Capability assignment for the Required Capability.", true, "Grant " + requestedSkill + " to at least one assigned Agent."));
        checks.add(dispatchableAgents > 0
                ? check("DISPATCHABLE_AGENT", "PASS", "At least one Agent is dispatchable for this Flow Rule dry-run.", false, null)
                : check("DISPATCHABLE_AGENT", "BLOCKED", "No Agent currently satisfies assignment, approval, readiness, and optional Required Capability checks.", true, "Fix the first blocking check above, then run dry-run again."));

        response.setChecks(checks);
        Optional<DispatchFlowReadinessCheck> firstBlock = checks.stream().filter(check -> Boolean.TRUE.equals(check.getBlocking())).findFirst();
        response.setReady(firstBlock.isEmpty());
        response.setDispatchable(firstBlock.isEmpty());
        response.setStatus(firstBlock.isEmpty() ? "READY" : "BLOCKED");
        if (firstBlock.isPresent()) {
            response.setFirstBlockingCode(firstBlock.get().getCode());
            response.setFirstBlockingReason(firstBlock.get().getMessage());
        }
        response.setSummary(firstBlock.isEmpty()
                ? "Flow Rule dry-run passed. Runtime should produce FLOW_RULE evidence and can select Agent " + response.getSelectedAgentId() + "."
                : "Flow Rule dry-run found a blocking gap before runtime assignment: " + response.getFirstBlockingCode() + ".");
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("mode", "P2_FLOW_RULE_DRY_RUN");
        diagnostics.put("legacyReadinessUsed", false);
        diagnostics.put("requiredCapabilityMatches", requiredCapabilityMatches);
        diagnostics.put("approvedFlowAgentAssignments", approvedAssignments);
        diagnostics.put("readyFlowAgentAssignments", readyAssignments);
        diagnostics.put("assignedAgentsWithRequiredCapability", agentsWithRequiredCapability);
        diagnostics.put("dispatchableAgentCount", dispatchableAgents);
        diagnostics.put("runtimeEvidenceExpected", Map.of("routingPath", "FLOW_RULE", "matchedFlowId", flowId, "matchedRuleId", ruleId, "requiredCapability", requestedSkill));
        response.setDiagnostics(diagnostics);
        if (Boolean.TRUE.equals(response.getDispatchable())) {
            log.info("dispatch_flow_dry_run_ready tenantId={} flowId={} ruleId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requiredCapability={} selectedAgentId={} candidateCount={} dispatchableAgentCount={}",
                    response.getTenantId(), response.getFlowId(), response.getRuleId(), response.getSourceSystem(), response.getEventStage(), response.getObjectType(),
                    response.getEventType(), response.getErrorCode(), requestedSkill, response.getSelectedAgentId(), agents.size(), dispatchableAgents);
        } else {
            log.warn("dispatch_flow_dry_run_blocked tenantId={} flowId={} ruleId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requiredCapability={} blockingCode={} reason={} candidateCount={} dispatchableAgentCount={}",
                    response.getTenantId(), response.getFlowId(), response.getRuleId(), response.getSourceSystem(), response.getEventStage(), response.getObjectType(),
                    response.getEventType(), response.getErrorCode(), requestedSkill, response.getFirstBlockingCode(), response.getFirstBlockingReason(), agents.size(), dispatchableAgents);
        }
        return response;
    }

    private Optional<Map<String, Object>> findBestRule(DispatchFlowReadinessRequest request) {
        String flowId = emptyToNull(request.getFlowId());
        String sourceSystem = normalizeNullable(request.getSourceSystem());
        String targetSystem = normalizeNullable(request.getTargetSystem());
        String eventType = normalizeNullable(request.getEventType());
        String objectType = normalizeNullable(request.getObjectType());
        String errorCode = normalizeNullable(request.getErrorCode());
        String requestedSkill = null;
        boolean hasFlowId = !blank(flowId);
        boolean hasSourceSystem = !blank(sourceSystem);
        boolean hasTargetSystem = !blank(targetSystem) && !"*".equals(targetSystem);
        boolean hasEventType = !blank(eventType) && !"*".equals(eventType);
        boolean hasObjectType = !blank(objectType) && !"*".equals(objectType);
        boolean hasErrorCode = !blank(errorCode) && !"*".equals(errorCode);
        boolean hasRequestedSkill = false;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantIds", tenantCandidates(request.getTenantId()))
                .addValue("tenantIdUpper", normalizeCode(request.getTenantId()))
                .addValue("hasFlowId", hasFlowId)
                .addValue("flowId", firstNonBlank(flowId, "__ANY_FLOW_ID__"))
                .addValue("hasSourceSystem", hasSourceSystem)
                .addValue("sourceSystem", firstNonBlank(sourceSystem, "__ANY_SOURCE_SYSTEM__"))
                .addValue("originSourceSystem", firstNonBlank(request.getOriginSourceSystem(), sourceSystem, "__ANY_SOURCE_SYSTEM__"))
                .addValue("hasTargetSystem", hasTargetSystem)
                .addValue("targetSystem", firstNonBlank(targetSystem, "__ANY_TARGET_SYSTEM__"))
                .addValue("eventStage", firstNonBlank(request.getEventStage(), "EXTERNAL"))
                .addValue("hasEventType", hasEventType)
                .addValue("eventType", firstNonBlank(eventType, "__ANY_EVENT_TYPE__"))
                .addValue("hasObjectType", hasObjectType)
                .addValue("objectType", firstNonBlank(objectType, "__ANY_OBJECT_TYPE__"))
                .addValue("hasErrorCode", hasErrorCode)
                .addValue("errorCode", firstNonBlank(errorCode, "__ANY_ERROR_CODE__"))
                .addValue("hasRequestedSkill", hasRequestedSkill)
                .addValue("requestedSkill", "__NO_REQUIRED_CAPABILITY_SELECTOR__");
        try {
            return Optional.ofNullable(jdbc.queryForMap("""
                    with candidate_rules as (
                        select
                            p.tenant_id,
                            p.flow_id,
                            f.flow_code,
                            f.flow_name,
                            p.policy_id as rule_id,
                            p.policy_code as rule_code,
                            p.policy_name as rule_name,
                            coalesce(nullif(upper(p.rule_scope), ''),
                                     case upper(coalesce(nullif(p.event_stage, ''), :eventStage))
                                          when 'A2A' then 'A2A_DISPATCH'
                                          when 'RESULT' then 'RESULT_CALLBACK'
                                          when 'ISSUE' then 'ISSUE_TRACKING'
                                          else 'EXTERNAL_INTAKE'
                                     end) as rule_scope,
                            upper(coalesce(nullif(p.event_stage, ''), :eventStage)) as event_stage,
                            upper(coalesce(nullif(p.source_system, ''), nullif(p.origin_source_system, ''), f.source_system)) as source_system,
                            upper(p.origin_source_system) as origin_source_system,
                            upper(p.target_system) as target_system,
                            upper(coalesce(nullif(p.event_type, ''), '*')) as event_type,
                            upper(coalesce(nullif(p.object_type, ''), '*')) as object_type,
                            upper(coalesce(nullif(p.error_code, ''), '*')) as error_code,
                            upper(p.requested_skill) as requested_skill,
                            upper(p.handoff_mode) as handoff_mode,
                            coalesce(string_agg(distinct upper(frs.skill_code), ',') filter (where frs.skill_code is not null and btrim(frs.skill_code) <> ''), '') as required_skills,
                            case when upper(p.tenant_id) = :tenantIdUpper then 1000 else 0 end
                            + case when upper(coalesce(nullif(p.event_stage, ''), :eventStage)) = :eventStage then 120 else 0 end
                            + case when upper(coalesce(nullif(p.event_type, ''), '*')) = coalesce(:eventType, '*') then 100 when coalesce(p.event_type, '*') = '*' then 10 else 0 end
                            + case when upper(coalesce(nullif(p.object_type, ''), '*')) = coalesce(:objectType, '*') then 60 when coalesce(p.object_type, '*') = '*' then 5 else 0 end
                            + case when upper(coalesce(nullif(p.error_code, ''), '*')) = coalesce(:errorCode, '*') then 60 when coalesce(p.error_code, '*') = '*' then 5 else 0 end
                            + 0 as match_score,
                            max(p.updated_at) as updated_at
                        from dispatch_policies p
                        join dispatch_flows f
                          on f.tenant_id = p.tenant_id
                         and f.flow_id = p.flow_id
                        left join flow_required_capabilities frs
                          on frs.tenant_id = p.tenant_id
                         and frs.flow_id = p.flow_id
                         and (frs.rule_id = p.policy_id or frs.rule_id is null)
                         and upper(coalesce(frs.event_stage, p.event_stage, :eventStage)) = upper(coalesce(p.event_stage, :eventStage))
                         and coalesce(frs.required, true) = true
                        where p.tenant_id in (:tenantIds)
                          and p.flow_id is not null
                          and (:hasFlowId = false or p.flow_id = :flowId)
                          and upper(coalesce(p.status, 'DRAFT')) in ('ACTIVE','ENABLED')
                          and upper(coalesce(f.status, 'DRAFT')) in ('ACTIVE','ENABLED')
                          and (:hasSourceSystem = false or upper(coalesce(nullif(p.source_system, ''), nullif(p.origin_source_system, ''), f.source_system)) in (:sourceSystem, '*'))
                          and upper(coalesce(nullif(p.event_stage, ''), :eventStage)) in (:eventStage, '*')
                          and (:hasTargetSystem = false or p.target_system is null or upper(p.target_system) in (:targetSystem, '*'))
                          and (:hasEventType = false or p.event_type is null or upper(p.event_type) in (:eventType, '*'))
                          and (:hasObjectType = false or p.object_type is null or upper(p.object_type) in (:objectType, '*'))
                          and (:hasErrorCode = false or p.error_code is null or upper(p.error_code) in (:errorCode, '*'))
                          and :hasRequestedSkill = false
                        group by p.tenant_id, p.flow_id, f.flow_code, f.flow_name, p.policy_id, p.policy_code, p.policy_name, p.rule_scope,
                                 p.event_stage, p.source_system, p.origin_source_system, f.source_system,
                                 p.target_system, p.event_type, p.object_type, p.error_code, p.requested_skill, p.handoff_mode
                    )
                    select * from candidate_rules
                    order by match_score desc, updated_at desc nulls last, rule_id asc
                    limit 1
                    """, params));
        } catch (EmptyResultDataAccessException ex) {
            log.warn("dispatch_flow_dry_run_no_rule tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requiredCapability={} flowId={} reason=NO_ACTIVE_FLOW_RULE",
                    request.getTenantId(), request.getSourceSystem(), request.getEventStage(), request.getObjectType(), request.getEventType(), request.getErrorCode(), null, request.getFlowId());
            return Optional.empty();
        } catch (DataAccessException ex) {
            log.error("dispatch_flow_dry_run_sql_failed tenantId={} sourceSystem={} eventStage={} objectType={} eventType={} errorCode={} requiredCapability={} flowId={} exception={} message={}",
                    request.getTenantId(), request.getSourceSystem(), request.getEventStage(), request.getObjectType(), request.getEventType(), request.getErrorCode(), null, request.getFlowId(),
                    ex.getClass().getSimpleName(), safeMessage(ex), ex);
            throw ex;
        }
    }

    private long countRequiredCapability(String tenantId, String flowId, String ruleId, String eventStage, String requestedSkill) {
        if (blank(requestedSkill)) return 0;
        Long count = jdbc.queryForObject("""
                select count(distinct frs.skill_code)::bigint
                  from flow_required_capabilities frs
                 where frs.tenant_id = :tenantId
                   and frs.flow_id = :flowId
                   and upper(coalesce(frs.event_stage, 'EXTERNAL')) in (:eventStage, '*')
                   and coalesce(frs.required, true) = true
                   and upper(frs.skill_code) = :requestedSkill
                   and (frs.rule_id is null or frs.rule_id = :ruleId)
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("flowId", flowId)
                .addValue("ruleId", ruleId)
                .addValue("eventStage", firstNonBlank(eventStage, "EXTERNAL"))
                .addValue("requestedSkill", requestedSkill), Long.class);
        return count == null ? 0 : count;
    }

    private List<DispatchFlowCandidateAgentView> candidateAgents(String tenantId, String flowId, String eventStage, String requestedSkill, String agentId) {
        RowMapper<DispatchFlowCandidateAgentView> mapper = (rs, rowNum) -> {
            DispatchFlowCandidateAgentView agent = new DispatchFlowCandidateAgentView();
            agent.setAgentId(rs.getString("agent_id"));
            agent.setAgentName(rs.getString("agent_name"));
            agent.setEventStage(rs.getString("event_stage"));
            agent.setAgentRole(rs.getString("agent_role"));
            agent.setAssignmentStatus(rs.getString("assignment_status"));
            agent.setApprovalStatus(rs.getString("approval_status"));
            agent.setReadinessStatus(rs.getString("readiness_status"));
            agent.setRuntimeStatus(rs.getString("runtime_status"));
            agent.setSkillGrantStatus(rs.getString("skill_grant_status"));
            agent.setAssignmentActive(rs.getBoolean("assignment_active"));
            agent.setApprovalReady(rs.getBoolean("approval_ready"));
            agent.setReadinessReady(rs.getBoolean("readiness_ready"));
            agent.setRequestedSkillGranted(rs.getBoolean("required_capability_granted"));
            agent.setDispatchable(rs.getBoolean("dispatchable"));
            List<String> reasons = new ArrayList<>();
            if (!Boolean.TRUE.equals(agent.getAssignmentActive())) reasons.add("ASSIGNMENT_NOT_ACTIVE");
            if (!Boolean.TRUE.equals(agent.getApprovalReady())) reasons.add("AGENT_NOT_APPROVED_FOR_FLOW");
            if (!Boolean.TRUE.equals(agent.getReadinessReady())) reasons.add("FLOW_AGENT_NOT_READY");
            if (!blank(requestedSkill) && !Boolean.TRUE.equals(agent.getRequestedSkillGranted())) reasons.add("REQUIRED_CAPABILITY_MISSING");
            agent.setBlockingReasons(reasons);
            return agent;
        };
        return jdbc.query("""
                select
                    faa.agent_id,
                    faa.agent_name,
                    upper(coalesce(faa.event_stage, 'EXTERNAL')) as event_stage,
                    upper(coalesce(faa.agent_role, 'LEAD')) as agent_role,
                    upper(coalesce(faa.assignment_status, 'DRAFT')) as assignment_status,
                    upper(coalesce(faa.approval_status, 'PENDING')) as approval_status,
                    upper(coalesce(faa.readiness_status, 'NOT_READY')) as readiness_status,
                    upper(coalesce(faa.runtime_status, 'UNKNOWN')) as runtime_status,
                    coalesce(max(upper(aca.status)), 'MISSING') as skill_grant_status,
                    upper(coalesce(faa.assignment_status, 'DRAFT')) in ('ACTIVE','ENABLED') as assignment_active,
                    upper(coalesce(faa.approval_status, 'PENDING')) in ('APPROVED','ACTIVE') as approval_ready,
                    upper(coalesce(faa.readiness_status, 'NOT_READY')) in ('READY','FLOW_AGENT_READY') as readiness_ready,
                    (:requiresCapability = false or count(aca.assignment_id) filter (where upper(coalesce(aca.status, 'PENDING')) in ('APPROVED','ACTIVE') and (aca.expires_at is null or aca.expires_at > now())) > 0) as required_capability_granted,
                    upper(coalesce(faa.assignment_status, 'DRAFT')) in ('ACTIVE','ENABLED')
                    and upper(coalesce(faa.approval_status, 'PENDING')) in ('APPROVED','ACTIVE')
                    and upper(coalesce(faa.readiness_status, 'NOT_READY')) in ('READY','FLOW_AGENT_READY')
                    and (:requiresCapability = false or count(aca.assignment_id) filter (where upper(coalesce(aca.status, 'PENDING')) in ('APPROVED','ACTIVE') and (aca.expires_at is null or aca.expires_at > now())) > 0) as dispatchable
                from flow_agent_assignments faa
                left join agent_capability_assignments aca
                  on aca.tenant_id = faa.tenant_id
                 and aca.agent_id = faa.agent_id
                 and (:requiresCapability = false or upper(aca.capability_code) = :requestedSkill)
                where faa.tenant_id = :tenantId
                  and faa.flow_id = :flowId
                  and upper(coalesce(faa.event_stage, 'EXTERNAL')) in (:eventStage, '*')
                  and (:hasAgentId = false or faa.agent_id = :agentId)
                group by faa.agent_id, faa.agent_name, faa.event_stage, faa.agent_role,
                         faa.assignment_status, faa.approval_status, faa.readiness_status, faa.runtime_status
                order by dispatchable desc, approval_ready desc, readiness_ready desc, required_capability_granted desc, faa.agent_id asc
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("flowId", flowId)
                .addValue("eventStage", firstNonBlank(eventStage, "EXTERNAL"))
                .addValue("requestedSkill", firstNonBlank(requestedSkill, "__NO_REQUIRED_CAPABILITY__"))
                .addValue("requiresCapability", !blank(requestedSkill))
                .addValue("hasAgentId", !blank(agentId))
                .addValue("agentId", firstNonBlank(agentId, "__ANY_AGENT_ID__")), mapper);
    }

    private static String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) return "-";
        return ex.getMessage().replace('\n', ' ').replace('\r', ' ');
    }

    private DispatchFlowReadinessRequest normalizeRequest(DispatchFlowReadinessRequest request) {
        DispatchFlowReadinessRequest normalized = request == null ? new DispatchFlowReadinessRequest() : request;
        normalized.setTenantId(requireNonBlank(normalized.getTenantId(), "tenantId").trim());
        normalized.setSourceSystem(normalizeCode(requireNonBlank(firstNonBlank(normalized.getSourceSystem(), normalized.getOriginSourceSystem()), "sourceSystem")));
        normalized.setOriginSourceSystem(normalizeNullable(normalized.getOriginSourceSystem()));
        normalized.setTargetSystem(normalizeNullable(normalized.getTargetSystem()));
        normalized.setEventStage(firstNonBlank(normalizeNullable(normalized.getEventStage()), "EXTERNAL"));
        normalized.setObjectType(normalizeWildcard(normalized.getObjectType()));
        normalized.setEventType(normalizeWildcard(normalized.getEventType()));
        normalized.setErrorCode(normalizeWildcard(normalized.getErrorCode()));
        normalized.setRequestedSkill(normalizeNullable(normalized.getRequestedSkill()));
        return normalized;
    }

    private DispatchFlowReadinessCheck check(String code, String status, String message, boolean blocking, String nextAction) {
        return DispatchFlowReadinessCheck.of(code, status, message, blocking, nextAction);
    }

    private DispatchFlowReadinessCheck check(String code, String status, String message, boolean blocking, String nextAction, Map<String, Object> details) {
        DispatchFlowReadinessCheck check = check(code, status, message, blocking, nextAction);
        check.setDetails(details);
        return check;
    }

    private static List<String> tenantCandidates(String tenantId) {
        return List.of(requireNonBlank(tenantId, "tenantId").trim());
    }

    private static List<String> splitSkills(String raw, String fallback) {
        List<String> skills = new ArrayList<>();
        if (!blank(raw)) {
            Arrays.stream(raw.split(","))
                    .map(DispatchFlowReadinessService::normalizeCode)
                    .filter(value -> !blank(value))
                    .forEach(skills::add);
        }
        if (skills.isEmpty() && !blank(fallback)) skills.add(fallback);
        return skills;
    }

    private static String firstSkill(String raw) {
        List<String> skills = splitSkills(raw, null);
        return skills.isEmpty() ? null : skills.get(0);
    }

    private static String normalizeCode(String value) {
        if (blank(value)) return null;
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return blank(value) ? null : normalizeCode(value);
    }

    private static String normalizeWildcard(String value) {
        if (blank(value)) return "*";
        String normalized = normalizeCode(value);
        return "ANY".equals(normalized) ? "*" : normalized;
    }

    private static String emptyToNull(String value) {
        return blank(value) ? null : value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (blank(value)) throw new IllegalArgumentException(fieldName + " is required");
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
