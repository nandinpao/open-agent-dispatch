package com.opensocket.aievent.core.dispatch.flow;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.nio.charset.StandardCharsets;
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
 * MRS A0-R4 legacy-equivalence evaluator.
 *
 * <p>This service never selects a production executor and never creates a TaskAssignment or
 * DispatchRequest. It converts the legacy Flow candidate contract into Flow-scoped DIRECT_AGENT
 * compatibility bindings and compares that representation with the actual legacy assignment.</p>
 */
@Service
public class FlowCapabilityLegacyEquivalenceService {
    static final String EVALUATOR_VERSION = "MRS_A0_R4_V1";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public FlowCapabilityLegacyEquivalenceService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public BridgeRefresh refreshBridge(String tenantId, String flowId, String actorRef) {
        String tenant = require(tenantId, "tenantId");
        String flow = require(flowId, "flowId");
        bindTenant(tenant);
        ensureFlow(tenant, flow);
        jdbc.queryForList("select pg_advisory_xact_lock(hashtext(:lockKey))",
                new MapSqlParameterSource("lockKey", tenant + ":a0r4:" + flow));
        OffsetDateTime sourceRevision = bridgeSourceRevision(tenant, flow);
        long revision = jdbc.queryForObject("""
                update flow_capability_migration_states
                   set compatibility_bridge_revision=compatibility_bridge_revision+1,
                       compatibility_bridge_source_revision=:sourceRevision,
                       updated_at=now(),updated_by=:actor
                 where tenant_id=:tenant and flow_id=:flow
             returning compatibility_bridge_revision
                """, new MapSqlParameterSource("tenant", tenant).addValue("flow", flow)
                .addValue("sourceRevision", sourceRevision).addValue("actor", actor(actorRef)), Long.class);

        jdbc.update("delete from flow_direct_agent_compatibility_bindings where tenant_id=:tenant and flow_id=:flow",
                new MapSqlParameterSource("tenant", tenant).addValue("flow", flow));

        List<LegacyBridgeInput> inputs = jdbc.query("""
                select faa.id as flow_agent_assignment_id,faa.event_stage,faa.agent_id,
                       faa.assignment_status,faa.approval_status,faa.updated_at as assignment_updated_at,
                       frc.skill_code as legacy_skill_code
                  from flow_agent_assignments faa
                  join flow_required_capabilities frc
                    on frc.tenant_id=faa.tenant_id and frc.flow_id=faa.flow_id and coalesce(frc.required,true)=true
                   and (upper(coalesce(faa.event_stage,'EXTERNAL'))='*' or upper(coalesce(frc.event_stage,'EXTERNAL')) in (upper(coalesce(faa.event_stage,'EXTERNAL')),'*'))
                   and (frc.agent_role is null or faa.agent_role is null or upper(frc.agent_role)=upper(faa.agent_role))
                 where faa.tenant_id=:tenant and faa.flow_id=:flow
                   and frc.skill_code is not null and btrim(frc.skill_code)<>''
                 order by faa.agent_id,faa.event_stage,frc.skill_code
                """, new MapSqlParameterSource("tenant", tenant).addValue("flow", flow),
                (rs, n) -> new LegacyBridgeInput(rs.getString("flow_agent_assignment_id"), rs.getString("event_stage"),
                        rs.getString("agent_id"), rs.getString("assignment_status"), rs.getString("approval_status"),
                        rs.getObject("assignment_updated_at", OffsetDateTime.class), rs.getString("legacy_skill_code")));

        List<BridgeRow> rows = new ArrayList<>();
        for (LegacyBridgeInput input : inputs) {
            CanonicalResolution canonical = resolveOne(tenant, input.legacySkillCode());
            ProviderLink provider = canonical.capabilityCode() == null ? null : providerLink(tenant, input.agentId());
            BindingLink binding = canonical.capabilityCode() == null || provider == null ? null
                    : approvedBinding(tenant, canonical.capabilityCode(), provider.providerId());
            String status;
            List<String> reasons = new ArrayList<>();
            if (!activeLegacyAssignment(input)) {
                status = "LEGACY_ASSIGNMENT_INACTIVE"; reasons.add(status);
            } else if (canonical.capabilityCode() == null) {
                status = "UNMAPPED_CAPABILITY"; reasons.add(status);
            } else if (provider == null) {
                status = "NO_MANAGED_PROVIDER_LINK"; reasons.add(status);
            } else if (binding == null) {
                status = "NO_APPROVED_CAPABILITY_BINDING"; reasons.add(status);
            } else {
                status = "READY"; reasons.add("LEGACY_DIRECT_AGENT_CAN_BE_REPRESENTED_CANONICALLY");
            }
            String bridgeId = "a0r4-direct-" + UUID.nameUUIDFromBytes(
                    (tenant + ":" + input.assignmentId() + ":" + input.legacySkillCode()).getBytes(StandardCharsets.UTF_8));
            jdbc.update("""
                    insert into flow_direct_agent_compatibility_bindings(
                      tenant_id,bridge_id,flow_id,flow_agent_assignment_id,event_stage,agent_id,legacy_skill_code,
                      capability_code,provider_id,capability_binding_id,binding_type,bridge_status,authority_mode,
                      side_effect_allowed,source_assignment_updated_at,source_binding_updated_at,bridge_revision,
                      reason_codes_json,refreshed_by,refreshed_at)
                    values(:tenant,:bridge,:flow,:assignment,:stage,:agent,:skill,:capability,:provider,:binding,
                      'DIRECT_AGENT',:status,'SHADOW_ONLY',false,:assignmentUpdated,:bindingUpdated,:revision,
                      cast(:reasons as jsonb),:actor,now())
                    """, new MapSqlParameterSource("tenant", tenant).addValue("bridge", bridgeId).addValue("flow", flow)
                    .addValue("assignment", input.assignmentId()).addValue("stage", input.eventStage())
                    .addValue("agent", input.agentId()).addValue("skill", input.legacySkillCode())
                    .addValue("capability", canonical.capabilityCode()).addValue("provider", provider == null ? null : provider.providerId())
                    .addValue("binding", binding == null ? null : binding.bindingId()).addValue("status", status)
                    .addValue("assignmentUpdated", input.updatedAt()).addValue("bindingUpdated", binding == null ? null : binding.updatedAt())
                    .addValue("revision", revision).addValue("reasons", write(reasons)).addValue("actor", actor(actorRef)));
            rows.add(new BridgeRow(bridgeId, input.assignmentId(), input.eventStage(), input.agentId(), input.legacySkillCode(),
                    canonical.capabilityCode(), provider == null ? null : provider.providerId(), binding == null ? null : binding.bindingId(),
                    status, List.copyOf(reasons), revision));
        }
        long ready = rows.stream().filter(r -> "READY".equals(r.bridgeStatus())).count();
        long blocked = rows.size() - ready;
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("flowId", flow); snapshot.put("bridgeRevision", revision); snapshot.put("rows", rows);
        String eventId = "a0r4-bridge-event-" + UUID.randomUUID();
        jdbc.update("""
                insert into flow_direct_agent_bridge_events(
                  tenant_id,event_id,flow_id,bridge_revision,bridge_snapshot_json,ready_count,blocked_count,refreshed_by,refreshed_at)
                values(:tenant,:event,:flow,:revision,cast(:snapshot as jsonb),:ready,:blocked,:actor,now())
                """, new MapSqlParameterSource("tenant", tenant).addValue("event", eventId).addValue("flow", flow)
                .addValue("revision", revision).addValue("snapshot", write(snapshot)).addValue("ready", ready)
                .addValue("blocked", blocked).addValue("actor", actor(actorRef)));
        return new BridgeRefresh(flow, revision, ready, blocked, List.copyOf(rows));
    }

    @Transactional(readOnly = true)
    public List<BridgeRow> bridge(String tenantId, String flowId) {
        String tenant = require(tenantId,"tenantId"); bindTenant(tenant);
        return jdbc.query("""
                select bridge_id,flow_agent_assignment_id,event_stage,agent_id,legacy_skill_code,capability_code,
                       provider_id,capability_binding_id,bridge_status,reason_codes_json,bridge_revision
                  from flow_direct_agent_compatibility_bindings
                 where tenant_id=:tenant and flow_id=:flow
                 order by agent_id,event_stage,legacy_skill_code
                """, new MapSqlParameterSource("tenant",tenant).addValue("flow",require(flowId,"flowId")),
                (rs,n)->new BridgeRow(rs.getString("bridge_id"),rs.getString("flow_agent_assignment_id"),rs.getString("event_stage"),
                        rs.getString("agent_id"),rs.getString("legacy_skill_code"),rs.getString("capability_code"),rs.getString("provider_id"),
                        rs.getString("capability_binding_id"),rs.getString("bridge_status"),parseStrings(rs.getString("reason_codes_json")),
                        rs.getLong("bridge_revision")));
    }

    @Transactional
    public LegacyEquivalenceEvidence evaluateTask(String tenantId, String flowId, String taskId, String assignmentId, String actorRef) {
        String tenant=require(tenantId,"tenantId"), flow=require(flowId,"flowId"), task=require(taskId,"taskId");
        bindTenant(tenant);
        List<BridgeRow> bridgeRows = ensureBridgeFresh(tenant, flow, actorRef);
        CanonicalTaskInput input = canonicalTaskInput(tenant,flow,task);
        String selected = blank(assignmentId) ? latestSelectedAgent(tenant,task) : selectedAgentForAssignment(tenant,task,assignmentId);
        String selectedAssignment = blank(assignmentId) ? latestAssignmentId(tenant,task) : assignmentId.trim();
        List<String> legacyCandidates = legacyCandidates(tenant,flow,input.eventStage());
        List<String> canonicalCapabilities = resolveAll(tenant,input.requiredCapabilities());
        LinkedHashSet<String> bridgeAgentIds = new LinkedHashSet<>();
        LinkedHashSet<String> bridgeBindingIds = new LinkedHashSet<>();
        Map<String,Set<String>> readyCapabilitiesByAgent = new LinkedHashMap<>();
        for (BridgeRow row : bridgeRows) {
            if (!"READY".equals(row.bridgeStatus()) || blank(row.capabilityCode())) continue;
            bridgeAgentIds.add(row.agentId());
            if (!blank(row.capabilityBindingId())) bridgeBindingIds.add(row.capabilityBindingId());
            readyCapabilitiesByAgent.computeIfAbsent(row.agentId(),k->new LinkedHashSet<>()).add(row.capabilityCode());
        }
        LinkedHashSet<String> required = new LinkedHashSet<>(canonicalCapabilities);
        List<String> fullyRepresentable = legacyCandidates.stream()
                .filter(agent -> readyCapabilitiesByAgent.getOrDefault(agent,Set.of()).containsAll(required)).toList();
        Set<String> legacySet = new LinkedHashSet<>(legacyCandidates);
        Set<String> representableSet = new LinkedHashSet<>(fullyRepresentable);
        boolean selectedEquivalent = !blank(selected) && representableSet.contains(selected);
        boolean candidateEquivalent = !legacySet.isEmpty() && legacySet.equals(representableSet);
        Comparison comparison = equivalenceComparison(input.matchResult(),required,legacySet,selected,selectedEquivalent,candidateEquivalent,bridgeRows);
        String evidenceId="a0r4-equivalence-"+UUID.nameUUIDFromBytes((tenant+":"+task+":"+input.taskVersion()).getBytes(StandardCharsets.UTF_8));
        MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("evidence",evidenceId).addValue("task",task)
                .addValue("taskVersion",input.taskVersion()).addValue("assignment",selectedAssignment).addValue("flow",flow)
                .addValue("rule",input.ruleId()).addValue("decision",input.decisionId()).addValue("selected",selected)
                .addValue("legacy",write(legacyCandidates)).addValue("caps",write(canonicalCapabilities))
                .addValue("bridgeAgents",write(List.copyOf(bridgeAgentIds))).addValue("bridgeBindings",write(List.copyOf(bridgeBindingIds)))
                .addValue("representable",write(fullyRepresentable)).addValue("selectedEq",selectedEquivalent)
                .addValue("candidateEq",candidateEquivalent).addValue("result",comparison.result()).addValue("reasons",write(comparison.reasons()))
                .addValue("actor",actor(actorRef));
        jdbc.update("""
                insert into flow_capability_legacy_equivalence_evidence(
                  tenant_id,evidence_id,task_id,task_version,assignment_id,flow_id,rule_id,flow_match_decision_id,
                  legacy_selected_agent_id,legacy_candidate_agents_json,required_capabilities_json,direct_bridge_agent_ids_json,
                  direct_bridge_binding_ids_json,fully_representable_agent_ids_json,selected_executor_equivalent,
                  candidate_set_equivalent,comparison_result,reason_codes_json,authority_mode,shadow_only,side_effect_allowed,
                  evaluator_version,evaluated_by,evaluated_at)
                values(:tenant,:evidence,:task,:taskVersion,:assignment,:flow,:rule,:decision,:selected,cast(:legacy as jsonb),
                  cast(:caps as jsonb),cast(:bridgeAgents as jsonb),cast(:bridgeBindings as jsonb),cast(:representable as jsonb),
                  :selectedEq,:candidateEq,:result,cast(:reasons as jsonb),'LEGACY_FLOW_DIRECT',true,false,:version,:actor,now())
                on conflict(tenant_id,task_id,task_version,evaluator_version) do nothing
                """, p.addValue("version",EVALUATOR_VERSION));
        return evidence(tenant,evidenceId,task,input.taskVersion());
    }

    @Transactional(readOnly = true)
    public List<LegacyEquivalenceEvidence> evidence(String tenantId,String flowId,int limit){
        String tenant=require(tenantId,"tenantId");bindTenant(tenant);
        return jdbc.query("""
                select evidence_id,task_id,task_version,assignment_id,flow_id,rule_id,flow_match_decision_id,legacy_selected_agent_id,
                       legacy_candidate_agents_json,required_capabilities_json,direct_bridge_agent_ids_json,direct_bridge_binding_ids_json,
                       fully_representable_agent_ids_json,selected_executor_equivalent,candidate_set_equivalent,comparison_result,
                       reason_codes_json,evaluator_version,evaluated_by,evaluated_at
                  from flow_capability_legacy_equivalence_evidence
                 where tenant_id=:tenant and flow_id=:flow order by evaluated_at desc,evidence_id desc limit :limit
                """,new MapSqlParameterSource("tenant",tenant).addValue("flow",require(flowId,"flowId"))
                .addValue("limit",Math.max(1,Math.min(limit<=0?100:limit,1000))),(rs,n)->mapEvidence(rs));
    }

    @Transactional(readOnly = true)
    public EquivalenceReadiness readiness(String tenantId,String flowId){
        String tenant=require(tenantId,"tenantId");bindTenant(tenant);
        Map<String,Object> r=jdbc.queryForMap("""
                select * from flow_capability_legacy_equivalence_readiness_v202
                 where tenant_id=:tenant and flow_id=:flow
                """,new MapSqlParameterSource("tenant",tenant).addValue("flow",require(flowId,"flowId")));
        long sample=num(r.get("sample_size")), selectedEq=num(r.get("selected_executor_equivalent_count")), candidateEq=num(r.get("candidate_set_equivalent_count"));
        long blocked=num(r.get("blocked_bridge_rows")); int minimum=(int)num(r.get("minimum_equivalence_samples"));
        double requiredRate=decimal(r.get("required_selected_equivalence_rate")), actualRate=decimal(r.get("selected_equivalence_rate"));
        List<String> blockers=new ArrayList<>();
        if(sample<minimum)blockers.add("INSUFFICIENT_EQUIVALENCE_SAMPLE");
        if(blocked>0)blockers.add("DIRECT_AGENT_BRIDGE_INCOMPLETE");
        if(actualRate+0.0000001d<requiredRate)blockers.add("SELECTED_EXECUTOR_EQUIVALENCE_BELOW_THRESHOLD");
        if(candidateEq<sample)blockers.add("LEGACY_CANDIDATE_SET_NOT_FULLY_REPRESENTABLE");
        boolean ready=blockers.isEmpty();
        return new EquivalenceReadiness(String.valueOf(r.get("flow_id")),String.valueOf(r.get("migration_state")),
                String.valueOf(r.get("authoritative_mode")),num(r.get("compatibility_bridge_revision")),sample,minimum,
                selectedEq,candidateEq,num(r.get("exact_equivalence_count")),blocked,actualRate,requiredRate,
                ready?"READY_FOR_COMPAT_CUTOVER":"SHADOW_EVALUATION",List.copyOf(blockers),r.get("last_evaluated_at"));
    }

    @Transactional
    public int enqueueHistorical(String tenantId,String flowId,int limit){
        String tenant=require(tenantId,"tenantId"),flow=require(flowId,"flowId");bindTenant(tenant);
        int bounded=Math.max(1,Math.min(limit<=0?100:limit,5000));
        return jdbc.update("""
                insert into flow_capability_shadow_work_items(tenant_id,work_item_id,task_id,assignment_id,flow_id,status,next_attempt_at)
                select t.tenant_id,'a0r4-shadow-'||md5(t.tenant_id||':'||a.assignment_id),t.task_id,a.assignment_id,:flow,'PENDING',now()
                  from task_assignments a join tasks t on t.task_id=a.task_id
                 where t.tenant_id=:tenant and coalesce(a.matched_flow_id,t.matched_flow_id)=:flow
                   and exists(select 1 from flow_capability_migration_states s where s.tenant_id=t.tenant_id and s.flow_id=:flow
                              and s.authoritative_mode='LEGACY_FLOW_DIRECT'
                              and s.migration_state in ('SHADOW_EVALUATION','READY_FOR_COMPAT_CUTOVER','CAPABILITY_READY'))
                   and not exists(select 1 from flow_capability_shadow_work_items w where w.tenant_id=t.tenant_id and w.assignment_id=a.assignment_id)
                 order by a.created_at desc limit :limit
                on conflict(tenant_id,assignment_id) do nothing
                """,new MapSqlParameterSource("tenant",tenant).addValue("flow",flow).addValue("limit",bounded));
    }

    @Transactional
    public List<WorkItem> claimDue(String tenantId,String workerId,int limit){
        String tenant=require(tenantId,"tenantId");bindTenant(tenant);int bounded=Math.max(1,Math.min(limit<=0?25:limit,200));
        List<WorkItem> items=jdbc.query("""
                select work_item_id,task_id,assignment_id,flow_id,attempt_count
                  from flow_capability_shadow_work_items
                 where tenant_id=:tenant and ((status in ('PENDING','RETRY_PENDING') and next_attempt_at<=now())
                    or (status='RUNNING' and claim_until<now()))
                 order by created_at,work_item_id limit :limit for update skip locked
                """,new MapSqlParameterSource("tenant",tenant).addValue("limit",bounded),
                (rs,n)->new WorkItem(rs.getString("work_item_id"),rs.getString("task_id"),rs.getString("assignment_id"),rs.getString("flow_id"),rs.getInt("attempt_count")+1));
        for(WorkItem item:items) jdbc.update("""
                update flow_capability_shadow_work_items set status='RUNNING',attempt_count=:attempt,claimed_by=:worker,
                       claim_until=now()+interval '2 minutes',last_error=null
                 where tenant_id=:tenant and work_item_id=:id
                """,new MapSqlParameterSource("tenant",tenant).addValue("id",item.workItemId()).addValue("attempt",item.attemptCount()).addValue("worker",workerId));
        return List.copyOf(items);
    }

    @Transactional
    public void complete(String tenantId,String workItemId){String tenant=require(tenantId,"tenantId");bindTenant(tenant);jdbc.update("""
            update flow_capability_shadow_work_items set status='COMPLETED',claim_until=null,claimed_by=null,completed_at=now()
             where tenant_id=:tenant and work_item_id=:id
            """,new MapSqlParameterSource("tenant",tenant).addValue("id",workItemId));}

    @Transactional
    public void fail(String tenantId,String workItemId,int attempt,String error){String tenant=require(tenantId,"tenantId");bindTenant(tenant);boolean terminal=attempt>=8;jdbc.update("""
            update flow_capability_shadow_work_items set status=:status,claim_until=null,claimed_by=null,last_error=:error,
                   next_attempt_at=now()+(least(300,power(2,least(:attempt,8)))::text||' seconds')::interval
             where tenant_id=:tenant and work_item_id=:id
            """,new MapSqlParameterSource("tenant",tenant).addValue("id",workItemId).addValue("attempt",attempt)
            .addValue("status",terminal?"FAILED":"RETRY_PENDING").addValue("error",error==null?"UNKNOWN":error.substring(0,Math.min(error.length(),1000))));}

    private List<BridgeRow> ensureBridgeFresh(String tenant,String flow,String actorRef){
        Map<String,Object> state=jdbc.queryForMap("select compatibility_bridge_revision,compatibility_bridge_source_revision from flow_capability_migration_states where tenant_id=:tenant and flow_id=:flow",new MapSqlParameterSource("tenant",tenant).addValue("flow",flow));
        long revision=num(state.get("compatibility_bridge_revision"));
        OffsetDateTime stored=(OffsetDateTime)state.get("compatibility_bridge_source_revision");
        OffsetDateTime current=bridgeSourceRevision(tenant,flow);
        if(revision<=0||stored==null||(current!=null&&current.isAfter(stored)))return refreshBridge(tenant,flow,actorRef).rows();
        return bridge(tenant,flow);
    }

    private OffsetDateTime bridgeSourceRevision(String tenant,String flow){
        return jdbc.queryForObject("""
                select max(updated_at) from (
                  select max(updated_at) updated_at from flow_agent_assignments where tenant_id=:tenant and flow_id=:flow
                  union all select max(updated_at) from flow_required_capabilities where tenant_id=:tenant and flow_id=:flow
                  union all select max(updated_at) from skill_capability_aliases where tenant_id=:tenant
                  union all select max(updated_at) from managed_agent_provider_links where tenant_id=:tenant
                  union all select max(updated_at) from capability_bindings where tenant_id=:tenant
                  union all select max(updated_at) from capability_definitions where tenant_id=:tenant
                ) revisions
                """,new MapSqlParameterSource("tenant",tenant).addValue("flow",flow),OffsetDateTime.class);
    }

    private CanonicalTaskInput canonicalTaskInput(String tenant,String flow,String task){
        try{return jdbc.queryForObject("""
                select t.task_id,coalesce(t.version,1) task_version,coalesce(t.event_stage,'EXTERNAL') event_stage,
                       d.decision_id,d.matched_rule_id,d.output_capability_requirements_json,d.match_result
                  from tasks t left join lateral(
                    select * from flow_match_decisions f where f.tenant_id=t.tenant_id and f.task_id=t.task_id
                      and f.decision_source='A0_R3_FLOW_MATCH_AUTHORITY' order by f.decided_at desc limit 1) d on true
                 where t.tenant_id=:tenant and t.task_id=:task and t.matched_flow_id=:flow
                """,new MapSqlParameterSource("tenant",tenant).addValue("task",task).addValue("flow",flow),(rs,n)->new CanonicalTaskInput(
                        rs.getString("task_id"),rs.getLong("task_version"),rs.getString("event_stage"),rs.getString("decision_id"),
                        rs.getString("matched_rule_id"),parseStrings(rs.getString("output_capability_requirements_json")),rs.getString("match_result")));
        }catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("Task is not associated with Flow: "+task);}}

    private List<String> legacyCandidates(String tenant,String flow,String stage){return jdbc.queryForList("""
            select distinct agent_id from flow_agent_assignments where tenant_id=:tenant and flow_id=:flow
              and upper(coalesce(event_stage,'EXTERNAL')) in (upper(:stage),'*')
              and upper(coalesce(assignment_status,'DRAFT')) in ('ACTIVE','ENABLED')
              and upper(coalesce(approval_status,'PENDING')) in ('APPROVED','ACTIVE') order by agent_id
            """,new MapSqlParameterSource("tenant",tenant).addValue("flow",flow).addValue("stage",stage),String.class);}
    private String latestSelectedAgent(String tenant,String task){List<String> v=jdbc.queryForList("select agent_id from task_assignments where tenant_id=:tenant and task_id=:task and agent_id is not null order by created_at desc,assignment_id desc limit 1",new MapSqlParameterSource("tenant",tenant).addValue("task",task),String.class);return v.isEmpty()?null:v.getFirst();}
    private String latestAssignmentId(String tenant,String task){List<String> v=jdbc.queryForList("select assignment_id from task_assignments where tenant_id=:tenant and task_id=:task order by created_at desc,assignment_id desc limit 1",new MapSqlParameterSource("tenant",tenant).addValue("task",task),String.class);return v.isEmpty()?null:v.getFirst();}
    private String selectedAgentForAssignment(String tenant,String task,String assignment){List<String> v=jdbc.queryForList("select agent_id from task_assignments where tenant_id=:tenant and task_id=:task and assignment_id=:assignment",new MapSqlParameterSource("tenant",tenant).addValue("task",task).addValue("assignment",assignment),String.class);return v.isEmpty()?null:v.getFirst();}

    private CanonicalResolution resolveOne(String tenant,String raw){if(blank(raw))return new CanonicalResolution(null);List<String> v=jdbc.queryForList("""
            select capability_code from (
              select c.capability_code,1 rank from capability_definitions c where c.tenant_id=:tenant and c.status='ACTIVE' and lower(c.capability_code)=lower(:skill)
              union all select a.capability_code,2 rank from skill_capability_aliases a join capability_definitions c on c.tenant_id=a.tenant_id and c.capability_code=a.capability_code and c.status='ACTIVE'
               where a.tenant_id=:tenant and a.status='ACTIVE' and upper(a.legacy_skill_code)=upper(:skill)) x order by rank,capability_code limit 1
            """,new MapSqlParameterSource("tenant",tenant).addValue("skill",raw.trim()),String.class);return new CanonicalResolution(v.isEmpty()?null:v.getFirst());}
    private List<String> resolveAll(String tenant,List<String> raw){LinkedHashSet<String> out=new LinkedHashSet<>();for(String v:raw==null?List.<String>of():raw){CanonicalResolution r=resolveOne(tenant,v);if(r.capabilityCode()!=null)out.add(r.capabilityCode());}return List.copyOf(out);}
    private ProviderLink providerLink(String tenant,String agent){List<ProviderLink> v=jdbc.query("select provider_id from managed_agent_provider_links where tenant_id=:tenant and agent_id=:agent and status='ACTIVE' order by provider_id limit 1",new MapSqlParameterSource("tenant",tenant).addValue("agent",agent),(rs,n)->new ProviderLink(rs.getString("provider_id")));return v.isEmpty()?null:v.getFirst();}
    private BindingLink approvedBinding(String tenant,String capability,String provider){List<BindingLink> v=jdbc.query("select binding_id,updated_at from capability_bindings where tenant_id=:tenant and capability_code=:cap and provider_id=:provider and trust_status='APPROVED' order by binding_id limit 1",new MapSqlParameterSource("tenant",tenant).addValue("cap",capability).addValue("provider",provider),(rs,n)->new BindingLink(rs.getString("binding_id"),rs.getObject("updated_at",OffsetDateTime.class)));return v.isEmpty()?null:v.getFirst();}
    private boolean activeLegacyAssignment(LegacyBridgeInput in){return Set.of("ACTIVE","ENABLED").contains(norm(in.assignmentStatus()))&&Set.of("APPROVED","ACTIVE").contains(norm(in.approvalStatus()));}
    private Comparison equivalenceComparison(String matchResult,Set<String> required,Set<String> legacy,String selected,boolean selectedEq,boolean candidateEq,List<BridgeRow> rows){
        if(!"MATCHED".equals(matchResult))return new Comparison("NO_CANONICAL_FLOW_MATCH",List.of("A0_R3_MATCHED_FLOW_REQUIRED"));
        if(required.isEmpty())return new Comparison("NO_REQUIRED_CAPABILITY",List.of("CANONICAL_REQUIRED_CAPABILITY_EMPTY"));
        if(legacy.isEmpty())return new Comparison("NO_LEGACY_CANDIDATE",List.of("LEGACY_FLOW_HAS_NO_ACTIVE_APPROVED_AGENT"));
        if(blank(selected))return new Comparison("NO_LEGACY_SELECTED_EXECUTOR",List.of("LEGACY_ASSIGNMENT_NOT_YET_OBSERVED"));
        if(rows.stream().anyMatch(r->!"READY".equals(r.bridgeStatus())))return new Comparison("BRIDGE_INCOMPLETE",List.of("DIRECT_AGENT_COMPATIBILITY_BRIDGE_INCOMPLETE"));
        if(!selectedEq)return new Comparison("LEGACY_SELECTED_NOT_REPRESENTABLE",List.of("SELECTED_AGENT_MISSING_REQUIRED_CANONICAL_BINDING"));
        if(candidateEq)return new Comparison("EXACT_LEGACY_EQUIVALENCE",List.of("SELECTED_EXECUTOR_PRESERVED","LEGACY_CANDIDATE_SET_PRESERVED","NO_RUNTIME_AUTHORITY_CHANGE"));
        return new Comparison("SELECTED_EXECUTOR_EQUIVALENT_CANDIDATE_GAP",List.of("SELECTED_EXECUTOR_PRESERVED","LEGACY_CANDIDATE_SET_NOT_FULLY_REPRESENTABLE"));
    }

    private LegacyEquivalenceEvidence evidence(String tenant,String id,String task,long version){return jdbc.queryForObject("""
            select evidence_id,task_id,task_version,assignment_id,flow_id,rule_id,flow_match_decision_id,legacy_selected_agent_id,
                   legacy_candidate_agents_json,required_capabilities_json,direct_bridge_agent_ids_json,direct_bridge_binding_ids_json,
                   fully_representable_agent_ids_json,selected_executor_equivalent,candidate_set_equivalent,comparison_result,reason_codes_json,
                   evaluator_version,evaluated_by,evaluated_at from flow_capability_legacy_equivalence_evidence
             where tenant_id=:tenant and task_id=:task and task_version=:version and evaluator_version=:evaluator
            """,new MapSqlParameterSource("tenant",tenant).addValue("task",task).addValue("version",version).addValue("evaluator",EVALUATOR_VERSION),(rs,n)->mapEvidence(rs));}
    private LegacyEquivalenceEvidence mapEvidence(java.sql.ResultSet rs)throws java.sql.SQLException{return new LegacyEquivalenceEvidence(rs.getString("evidence_id"),rs.getString("task_id"),rs.getLong("task_version"),rs.getString("assignment_id"),rs.getString("flow_id"),rs.getString("rule_id"),rs.getString("flow_match_decision_id"),rs.getString("legacy_selected_agent_id"),parseStrings(rs.getString("legacy_candidate_agents_json")),parseStrings(rs.getString("required_capabilities_json")),parseStrings(rs.getString("direct_bridge_agent_ids_json")),parseStrings(rs.getString("direct_bridge_binding_ids_json")),parseStrings(rs.getString("fully_representable_agent_ids_json")),rs.getBoolean("selected_executor_equivalent"),rs.getBoolean("candidate_set_equivalent"),rs.getString("comparison_result"),parseStrings(rs.getString("reason_codes_json")),rs.getString("evaluator_version"),rs.getString("evaluated_by"),rs.getObject("evaluated_at",OffsetDateTime.class));}
    private void ensureFlow(String tenant,String flow){Integer c=jdbc.queryForObject("select count(*) from dispatch_flows where tenant_id=:tenant and flow_id=:flow",new MapSqlParameterSource("tenant",tenant).addValue("flow",flow),Integer.class);if(c==null||c==0)throw new IllegalArgumentException("Dispatch Flow not found: "+flow);}
    private void bindTenant(String tenant){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);if(c!=null&&!"INSTANCE".equalsIgnoreCase(c.tenantId())&&!tenant.equals(c.tenantId()))throw new IllegalArgumentException("Tenant context mismatch");String actor=c==null||blank(c.actorId())?"a0-r4-shadow-bridge":c.actorId();jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private List<String> parseStrings(String s){if(blank(s))return List.of();try{return List.copyOf(json.readValue(s,STRING_LIST));}catch(Exception e){return List.of();}}
    private String write(Object v){try{return json.writeValueAsString(v==null?List.of():v);}catch(Exception e){throw new IllegalStateException("A0-R4 JSON serialization failed",e);}}
    private static String require(String v,String f){if(blank(v))throw new IllegalArgumentException(f+" is required");return v.trim();}
    private static String actor(String ignoredClientValue){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);return c==null||blank(c.actorId())?"A0_R4_SYSTEM":c.actorId().trim();}
    private static String norm(String v){return blank(v)?"":v.trim().toUpperCase(Locale.ROOT);}
    private static boolean blank(String v){return v==null||v.isBlank();}
    private static long num(Object v){return v instanceof Number n?n.longValue():0L;}
    private static double decimal(Object v){return v instanceof Number n?n.doubleValue():0d;}

    record LegacyBridgeInput(String assignmentId,String eventStage,String agentId,String assignmentStatus,String approvalStatus,OffsetDateTime updatedAt,String legacySkillCode){}
    record CanonicalResolution(String capabilityCode){}
    record ProviderLink(String providerId){}
    record BindingLink(String bindingId,OffsetDateTime updatedAt){}
    record CanonicalTaskInput(String taskId,long taskVersion,String eventStage,String decisionId,String ruleId,List<String> requiredCapabilities,String matchResult){}
    record Comparison(String result,List<String> reasons){}
    public record BridgeRow(String bridgeId,String flowAgentAssignmentId,String eventStage,String agentId,String legacySkillCode,String capabilityCode,String providerId,String capabilityBindingId,String bridgeStatus,List<String> reasonCodes,long bridgeRevision){}
    public record BridgeRefresh(String flowId,long bridgeRevision,long readyCount,long blockedCount,List<BridgeRow> rows){}
    public record LegacyEquivalenceEvidence(String evidenceId,String taskId,long taskVersion,String assignmentId,String flowId,String ruleId,String flowMatchDecisionId,String legacySelectedAgentId,List<String> legacyCandidateAgentIds,List<String> requiredCapabilities,List<String> directBridgeAgentIds,List<String> directBridgeBindingIds,List<String> fullyRepresentableAgentIds,boolean selectedExecutorEquivalent,boolean candidateSetEquivalent,String comparisonResult,List<String> reasonCodes,String evaluatorVersion,String evaluatedBy,OffsetDateTime evaluatedAt){}
    public record EquivalenceReadiness(String flowId,String migrationState,String authoritativeMode,long compatibilityBridgeRevision,long sampleSize,int minimumSampleSize,long selectedExecutorEquivalentCount,long candidateSetEquivalentCount,long exactEquivalenceCount,long blockedBridgeRows,double selectedEquivalenceRate,double requiredSelectedEquivalenceRate,String recommendedState,List<String> blockers,Object lastEvaluatedAt){}
    public record WorkItem(String workItemId,String taskId,String assignmentId,String flowId,int attemptCount){}
}
