package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.assignment.ExecutionSafetyMode;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.release.ProductionFoundationReleaseGateService;
import java.time.OffsetDateTime;
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
 * A0-R7 execution-safety authority.
 *
 * <p>For a per-Flow NEW_AUTHORITATIVE cutover only, this service reruns the R6 routing evaluation
 * and atomically persists authoritative ExecutionAssignment + ExecutionLease + DispatchIntent.
 * It performs no network I/O. The durable intent is consumed only after transaction commit.</p>
 */
@Service
public class ExecutionSafetyAuthorityService {
    private static final TypeReference<Map<String,Object>> OBJECT_MAP=new TypeReference<>(){};
    private static final Set<String> STATES=Set.of("LEGACY_AUTHORITATIVE","SHADOW","NEW_AUTHORITATIVE");
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RoutingAuthorityCutoverService routing;
    private final ProductionFoundationReleaseGateService productionReleaseGate;

    public ExecutionSafetyAuthorityService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,RoutingAuthorityCutoverService routing,ProductionFoundationReleaseGateService productionReleaseGate){
        this.jdbc=jdbc;this.json=json;this.routing=routing;this.productionReleaseGate=productionReleaseGate;
    }

    @Transactional
    public FlowRoutingMigrationState setFlowAuthority(String tenantId,String flowId,ExecutionSafetyActivationRequest request){
        String t=tenant(tenantId),f=required(flowId,"flowId");bind(t);
        if(request==null)throw new IllegalArgumentException("Execution authority activation body is required");
        String next=upper(request.targetState());if(!STATES.contains(next))throw new IllegalArgumentException("targetState must be LEGACY_AUTHORITATIVE, SHADOW or NEW_AUTHORITATIVE");
        String reason=required(request.reason(),"reason");
        String releaseCandidate=trim(request.releaseCandidateId());
        FlowRoutingMigrationState old=flowState(t,f);
        if("NEW_AUTHORITATIVE".equals(next)){
            Integer shadow=jdbc.queryForObject("select count(*) from execution_assignment_shadows s join tasks t on t.tenant_id=s.tenant_id and t.task_id=s.task_id where s.tenant_id=:tenant and t.matched_flow_id=:flow",new MapSqlParameterSource("tenant",t).addValue("flow",f),Integer.class);
            if(shadow==null||shadow<1)throw new IllegalArgumentException("A0_R7_NEW_AUTHORITY_REQUIRES_R6_SHADOW_EVIDENCE");
            // Null candidate preserves R7 controlled-live for certification. A non-null candidate is
            // a Stage10 production-bound promotion and must be ACTIVE with the current release gate PASS.
            if(!blank(releaseCandidate))productionReleaseGate.assertActiveCandidate(t,releaseCandidate);
        } else {
            releaseCandidate=null;
        }
        int version=old==null?1:old.version()+1;OffsetDateTime at=OffsetDateTime.now();
        if(old!=null&&"NEW_AUTHORITATIVE".equals(old.migrationState())&&!"NEW_AUTHORITATIVE".equals(next)) quiesceFlowBeforeAuthorityDowngrade(t,f,reason,at);
        jdbc.update("insert into flow_routing_migration_state(tenant_id,flow_id,migration_state,version,change_reason,changed_by,changed_at,production_release_candidate_id) values(:tenant,:flow,:state,:version,:reason,:actor,:at,:candidate) on conflict(tenant_id,flow_id) do update set migration_state=excluded.migration_state,version=excluded.version,change_reason=excluded.change_reason,changed_by=excluded.changed_by,changed_at=excluded.changed_at,production_release_candidate_id=excluded.production_release_candidate_id",
                new MapSqlParameterSource("tenant",t).addValue("flow",f).addValue("state",next).addValue("version",version).addValue("reason",reason).addValue("actor",actor()).addValue("at",at).addValue("candidate",releaseCandidate));
        jdbc.update("insert into flow_routing_migration_events(tenant_id,event_id,flow_id,from_state,to_state,version,reason,actor_ref,occurred_at,production_release_candidate_id) values(:tenant,:id,:flow,:from,:to,:version,:reason,:actor,:at,:candidate)",
                new MapSqlParameterSource("tenant",t).addValue("id","flow-routing-r7-"+UUID.randomUUID()).addValue("flow",f).addValue("from",old==null?null:old.migrationState()).addValue("to",next).addValue("version",version).addValue("reason",reason).addValue("actor",actor()).addValue("at",at).addValue("candidate",releaseCandidate));
        return flowState(t,f);
    }

    @Transactional
    public ExecutionSafetyPrepareResult prepare(String tenantId,String planId,ExecutionSafetyPrepareRequest request){
        String t=tenant(tenantId),p=required(planId,"planId");bind(t);if(request==null)throw new IllegalArgumentException("Execution safety request body is required");
        int ttl=request.leaseTtlSeconds()==null?60:Math.max(15,Math.min(request.leaseTtlSeconds(),900));
        RoutingAuthorityShadowResult evidence=routing.evaluateShadow(t,p,new RoutingAuthorityShadowRequest(request.planRevision(),required(request.stepId(),"stepId"),required(request.routingProfileId(),"routingProfileId")));
        if(evidence.routingDecision()==null||!"SELECTED".equals(evidence.routingDecision().result())||evidence.executionAssignment()==null)
            throw new IllegalArgumentException("A0_R7_REQUIRES_SELECTED_R6_ROUTING_AND_SHADOW_ASSIGNMENT");
        ExecutionAssignmentShadow shadow=evidence.executionAssignment();
        String flowId=flowId(t,shadow.taskId());FlowRoutingMigrationState state=flowState(t,flowId);
        if(state==null||!"NEW_AUTHORITATIVE".equals(state.migrationState()))throw new IllegalArgumentException("FLOW_NOT_NEW_AUTHORITATIVE: "+flowId);
        StepSafety step=step(t,p,shadow.planRevision(),shadow.stepId());
        AdapterSafety adapter=adapter(t,shadow.selectedAdapterId());
        ExecutionSafetyMode mode=safetyMode(adapter,step);
        String approval=trim(request.humanApprovalRef());
        if(mode==ExecutionSafetyMode.REMOTE_UNFENCED&&"WRITE".equals(step.sideEffect())&&!"IDEMPOTENT".equals(step.writeSemantics())&&blank(approval))
            throw new IllegalArgumentException("REMOTE_UNFENCED_NON_IDEMPOTENT_WRITE_REQUIRES_HUMAN_APPROVAL");
        if(!"MANAGED_AGENT_NETTY".equals(adapter.adapterType()))
            throw new IllegalArgumentException("A0_R7_CONTROLLED_LIVE_SUPPORTS_MANAGED_AGENT_NETTY_ONLY; external MCP/A2A stays non-authoritative until its protocol runtime gate");
        if(blank(shadow.selectedAgentId()))throw new IllegalArgumentException("MANAGED_AGENT_NETTY_REQUIRES_SELECTED_AGENT");

        expireStaleLease(t,shadow.taskId(),shadow.stepId());
        long fence=nextFence(t,shadow.taskId(),shadow.stepId());OffsetDateTime now=OffsetDateTime.now(),until=now.plusSeconds(ttl);
        String assignmentId="exec-assignment-"+UUID.randomUUID(),leaseId="exec-lease-"+UUID.randomUUID(),intentId="dispatch-intent-"+UUID.randomUUID();
        String reason="A0-R7 promoted R6 shadow assignment="+shadow.assignmentId()+" binding="+shadow.bindingId()+" provider="+shadow.providerId();

        MapSqlParameterSource a=new MapSqlParameterSource("tenant",t).addValue("assignment",assignmentId).addValue("shadow",shadow.assignmentId()).addValue("task",shadow.taskId()).addValue("plan",p).addValue("rev",shadow.planRevision()).addValue("step",shadow.stepId()).addValue("flow",flowId).addValue("routing",shadow.routingDecisionId()).addValue("envelope",evidence.routingDecision().envelopeId()).addValue("binding",shadow.bindingId()).addValue("provider",shadow.providerId()).addValue("providerType",adapter.providerType()).addValue("pool",shadow.agentPoolId()).addValue("agent",shadow.selectedAgentId()).addValue("session",shadow.selectedSessionId()).addValue("peer",shadow.selectedPeerInterfaceId()).addValue("mcp",shadow.selectedMcpServerId()).addValue("adapter",shadow.selectedAdapterId()).addValue("adapterType",adapter.adapterType()).addValue("safety",mode.name()).addValue("side",step.sideEffect()).addValue("write",step.writeSemantics()).addValue("approval",approval).addValue("lease",leaseId).addValue("fence",fence).addValue("until",until).addValue("reason",reason).addValue("now",now);
        // §122A ordering: acquire the durable ExecutionLease first (its assignment FK is deferred),
        // then persist the canonical ExecutionAssignment and finally the DispatchIntent in the same TX.
        jdbc.update("insert into execution_leases_v206(tenant_id,lease_id,task_id,plan_id,plan_revision,step_id,assignment_id,owner_node_id,fencing_token,lease_until,status,acquired_at,version) values(:tenant,:lease,:task,:plan,:rev,:step,:assignment,:owner,:fence,:until,'ACTIVE',:now,1)",new MapSqlParameterSource(a.getValues()).addValue("owner",required(request.ownerNodeId(),"ownerNodeId")));
        jdbc.update("insert into execution_assignments_v206(tenant_id,assignment_id,shadow_assignment_id,task_id,plan_id,plan_revision,step_id,flow_id,routing_decision_id,envelope_id,binding_id,provider_id,provider_type,agent_pool_id,selected_agent_id,selected_session_id,selected_peer_interface_id,selected_mcp_server_id,selected_adapter_id,adapter_type,execution_safety_mode,side_effect,write_semantics,human_approval_ref,lease_id,fencing_token,lease_until,attempt_number,authority_mode,status,assignment_reason,created_at) values(:tenant,:assignment,:shadow,:task,:plan,:rev,:step,:flow,:routing,:envelope,:binding,:provider,:providerType,:pool,:agent,:session,:peer,:mcp,:adapter,:adapterType,:safety,:side,:write,:approval,:lease,:fence,:until,1,'NEW_AUTHORITATIVE','ASSIGNED',:reason,:now)",a);
        mirrorManagedAssignment(a,assignmentId,leaseId,fence,until,reason,flowId);
        Map<String,Object> payload=Map.of("input",request.input(),"routingDecisionId",shadow.routingDecisionId(),"envelopeId",evidence.routingDecision().envelopeId(),"shadowAssignmentId",shadow.assignmentId(),"canonicalAssignmentId",assignmentId);
        jdbc.update("insert into execution_dispatch_intents_v206(tenant_id,intent_id,assignment_id,lease_id,fencing_token,task_id,plan_id,plan_revision,step_id,flow_id,binding_id,provider_id,provider_type,selected_adapter_id,adapter_type,execution_safety_mode,side_effect,write_semantics,payload_json,status,created_at,updated_at) values(:tenant,:intent,:assignment,:lease,:fence,:task,:plan,:rev,:step,:flow,:binding,:provider,:providerType,:adapter,:adapterType,:safety,:side,:write,cast(:payload as jsonb),'PENDING',:now,:now)",new MapSqlParameterSource(a.getValues()).addValue("intent",intentId).addValue("payload",write(payload)));
        appendIntentEvent(t,intentId,assignmentId,null,"PENDING","ASSIGNMENT_LEASE_INTENT_COMMITTED",Map.of("fencingToken",fence,"leaseId",leaseId,"safetyMode",mode.name()));
        return new ExecutionSafetyPrepareResult(evidence,assignment(t,assignmentId),lease(t,leaseId),intent(t,intentId),state.migrationState());
    }

    @Transactional(readOnly=true) public java.util.List<Map<String,Object>> trace(String tenantId,String planId,int limit){String t=tenant(tenantId);bind(t);return jdbc.queryForList("select * from execution_safety_trace_v206 where tenant_id=:tenant and plan_id=:plan order by created_at desc limit :limit",new MapSqlParameterSource("tenant",t).addValue("plan",required(planId,"planId")).addValue("limit",Math.max(1,Math.min(limit<=0?100:limit,500))));}

    private void mirrorManagedAssignment(MapSqlParameterSource a,String assignmentId,String leaseId,long fence,OffsetDateTime until,String reason,String flowId){
        AgentRuntime r=agentRuntime(str(a.getValue("tenant")),str(a.getValue("agent")));
        jdbc.update("insert into task_assignments(tenant_id,assignment_id,task_id,incident_id,agent_id,agent_type,owner_gateway_node_id,agent_session_id,site_id,matched_flow_id,assigned_pool_id,routing_path,status,routing_policy,routing_decision_id,lease_id,fencing_token,lease_expires_at,binding_id,execution_target_type,provider_type,provider_id,execution_safety_mode,attempt_number,score,reason,capacity_reserved,created_at,updated_at,execution_authority_version,canonical_execution_assignment_id) select :tenant,:assignment,t.task_id,t.incident_id,:agent,:agentType,:gateway,:session,:site,:flow,:pool,'A0_R7_NEW_AUTHORITY','ASSIGNED','CAPABILITY_GOVERNED',:routing,:lease,cast(:fence as varchar),:until,:binding,'MANAGED_AGENT','MANAGED_AGENT',:provider,'LOCAL_FENCED',1,100,:reason,false,:now,:now,'A0-R7-V206',:assignment from tasks t where t.tenant_id=:tenant and t.task_id=:task",
                new MapSqlParameterSource(a.getValues()).addValue("agentType",r.agentType()).addValue("gateway",r.gatewayNode()).addValue("session",first(str(a.getValue("session")),r.sessionId())).addValue("site",r.siteId()));
    }
    private void quiesceFlowBeforeAuthorityDowngrade(String t,String flow,String reason,OffsetDateTime at){
        // If network has already started, outcome is unknown and must be reconciled; never pretend cancellation stopped remote/local execution.
        jdbc.update("insert into execution_dispatch_intent_events_v206(tenant_id,event_id,intent_id,assignment_id,from_status,to_status,reason_code,actor_ref,evidence_json,occurred_at) select tenant_id,'dispatch-intent-event-'||gen_random_uuid(),intent_id,assignment_id,status,case when status='SEND_STARTED' then 'DELIVERY_UNKNOWN' else 'CANCELLED' end,'FLOW_AUTHORITY_DOWNGRADED',:actor,jsonb_build_object('reason',:reason),:at from execution_dispatch_intents_v206 where tenant_id=:tenant and flow_id=:flow and status in ('PENDING','CLAIMED','HANDED_OFF','SEND_STARTED')",new MapSqlParameterSource("tenant",t).addValue("flow",flow).addValue("actor",actor()).addValue("reason",reason).addValue("at",at));
        jdbc.update("update execution_dispatch_intents_v206 set status=case when status='SEND_STARTED' then 'DELIVERY_UNKNOWN' else 'CANCELLED' end,delivery_unknown_since=case when status='SEND_STARTED' then coalesce(delivery_unknown_since,:at) else delivery_unknown_since end,last_error_code=case when status='SEND_STARTED' then 'FLOW_AUTHORITY_DOWNGRADED_DURING_SEND' else last_error_code end,last_error_message=case when status='SEND_STARTED' then :reason else last_error_message end,claimed_by=null,claim_token=null,claim_until=null,updated_at=:at where tenant_id=:tenant and flow_id=:flow and status in ('PENDING','CLAIMED','HANDED_OFF','SEND_STARTED')",new MapSqlParameterSource("tenant",t).addValue("flow",flow).addValue("reason",reason).addValue("at",at));
        jdbc.update("update execution_leases_v206 set status='REVOKED',released_at=:at,release_reason='FLOW_AUTHORITY_DOWNGRADED: '||:reason,version=version+1 where tenant_id=:tenant and assignment_id in(select assignment_id from execution_assignments_v206 where tenant_id=:tenant and flow_id=:flow) and status='ACTIVE'",new MapSqlParameterSource("tenant",t).addValue("flow",flow).addValue("reason",reason).addValue("at",at));
        jdbc.update("update execution_assignments_v206 set status='RELEASED',released_at=:at,outcome='AUTHORITY_REVOKED' where tenant_id=:tenant and flow_id=:flow and status='ASSIGNED'",new MapSqlParameterSource("tenant",t).addValue("flow",flow).addValue("at",at));
    }

    private long nextFence(String t,String task,String step){Long v=jdbc.queryForObject("insert into execution_fencing_counters(tenant_id,task_id,step_key,last_token,updated_at) values(:tenant,:task,:step,1,now()) on conflict(tenant_id,task_id,step_key) do update set last_token=execution_fencing_counters.last_token+1,updated_at=now() returning last_token",new MapSqlParameterSource("tenant",t).addValue("task",task).addValue("step",step),Long.class);if(v==null)throw new IllegalStateException("FENCING_TOKEN_ALLOCATION_FAILED");return v;}
    private void expireStaleLease(String t,String task,String step){jdbc.update("update execution_leases_v206 set status='EXPIRED',released_at=now(),release_reason='LEASE_TTL_EXPIRED',version=version+1 where tenant_id=:tenant and task_id=:task and step_id=:step and status='ACTIVE' and lease_until<=now()",new MapSqlParameterSource("tenant",t).addValue("task",task).addValue("step",step));}
    private ExecutionSafetyMode safetyMode(AdapterSafety a,StepSafety s){if("MANAGED_AGENT_NETTY".equals(a.adapterType())||"INTERNAL_SERVICE".equals(a.adapterType()))return ExecutionSafetyMode.LOCAL_FENCED;Object nativeIdem=a.config().get("nativeIdempotencySupported");if(Boolean.TRUE.equals(nativeIdem)||"true".equalsIgnoreCase(String.valueOf(nativeIdem)))return ExecutionSafetyMode.REMOTE_NATIVE_IDEMPOTENT;return ExecutionSafetyMode.REMOTE_UNFENCED;}
    private String flowId(String t,String task){try{return jdbc.queryForObject("select matched_flow_id from tasks where tenant_id=:tenant and task_id=:task",new MapSqlParameterSource("tenant",t).addValue("task",task),String.class);}catch(EmptyResultDataAccessException e){throw new IllegalArgumentException("Task not found: "+task);}}
    private StepSafety step(String t,String p,int rev,String step){return jdbc.queryForObject("select side_effect,write_semantics from execution_plan_revision_steps where tenant_id=:tenant and plan_id=:plan and revision=:rev and step_id=:step",new MapSqlParameterSource("tenant",t).addValue("plan",p).addValue("rev",rev).addValue("step",step),(rs,n)->new StepSafety(rs.getString("side_effect"),rs.getString("write_semantics")));}
    private AdapterSafety adapter(String t,String id){try{return jdbc.queryForObject("select provider_type,adapter_type,configuration_json from execution_adapter_registrations where tenant_id=:tenant and adapter_id=:id and status='ACTIVE'",new MapSqlParameterSource("tenant",t).addValue("id",required(id,"selectedAdapterId")),(rs,n)->new AdapterSafety(rs.getString("provider_type"),rs.getString("adapter_type"),readMap(rs.getString("configuration_json"))));}catch(EmptyResultDataAccessException e){throw new IllegalArgumentException("Selected Execution Adapter is not ACTIVE: "+id);}}
    private AgentRuntime agentRuntime(String t,String agent){try{return jdbc.queryForObject("select a.agent_type,a.owner_gateway_node_id,a.agent_session_id,a.site_id from agents a join agent_profiles p on p.agent_id=a.agent_id and p.tenant_id=:tenant where a.agent_id=:agent and p.approval_status='APPROVED' and p.enabled=true and a.status in ('CONNECTED','IDLE','BUSY_ACCEPTING')",new MapSqlParameterSource("tenant",t).addValue("agent",agent),(rs,n)->new AgentRuntime(rs.getString("agent_type"),rs.getString("owner_gateway_node_id"),rs.getString("agent_session_id"),rs.getString("site_id")));}catch(EmptyResultDataAccessException e){throw new IllegalArgumentException("Selected Agent is not currently approved/connected: "+agent);}}
    private FlowRoutingMigrationState flowState(String t,String f){if(blank(f))return null;try{return jdbc.queryForObject("select * from flow_routing_migration_state where tenant_id=:tenant and flow_id=:flow",new MapSqlParameterSource("tenant",t).addValue("flow",f),(rs,n)->new FlowRoutingMigrationState(rs.getString("tenant_id"),rs.getString("flow_id"),rs.getString("migration_state"),rs.getInt("version"),rs.getString("change_reason"),rs.getString("changed_by"),rs.getObject("changed_at",OffsetDateTime.class)));}catch(EmptyResultDataAccessException e){return new FlowRoutingMigrationState(t,f,"LEGACY_AUTHORITATIVE",0,"IMPLICIT_DEFAULT","system",null);}}
    private ExecutionAssignmentV206 assignment(String t,String id){return jdbc.queryForObject("select * from execution_assignments_v206 where tenant_id=:tenant and assignment_id=:id",new MapSqlParameterSource("tenant",t).addValue("id",id),(rs,n)->new ExecutionAssignmentV206(rs.getString("assignment_id"),rs.getString("tenant_id"),rs.getString("shadow_assignment_id"),rs.getString("task_id"),rs.getString("plan_id"),rs.getInt("plan_revision"),rs.getString("step_id"),rs.getString("flow_id"),rs.getString("routing_decision_id"),rs.getString("envelope_id"),rs.getString("binding_id"),rs.getString("provider_id"),rs.getString("provider_type"),rs.getString("agent_pool_id"),rs.getString("selected_agent_id"),rs.getString("selected_session_id"),rs.getString("selected_peer_interface_id"),rs.getString("selected_mcp_server_id"),rs.getString("selected_adapter_id"),rs.getString("adapter_type"),rs.getString("execution_safety_mode"),rs.getString("side_effect"),rs.getString("write_semantics"),rs.getString("human_approval_ref"),rs.getString("lease_id"),rs.getLong("fencing_token"),rs.getObject("lease_until",OffsetDateTime.class),rs.getInt("attempt_number"),rs.getString("previous_assignment_id"),rs.getString("authority_mode"),rs.getString("status"),rs.getString("assignment_reason"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("released_at",OffsetDateTime.class),rs.getString("outcome")));}
    private ExecutionLeaseV206 lease(String t,String id){return jdbc.queryForObject("select * from execution_leases_v206 where tenant_id=:tenant and lease_id=:id",new MapSqlParameterSource("tenant",t).addValue("id",id),(rs,n)->new ExecutionLeaseV206(rs.getString("lease_id"),rs.getString("tenant_id"),rs.getString("task_id"),rs.getString("plan_id"),rs.getInt("plan_revision"),rs.getString("step_id"),rs.getString("assignment_id"),rs.getString("owner_node_id"),rs.getLong("fencing_token"),rs.getObject("lease_until",OffsetDateTime.class),rs.getString("status"),rs.getObject("acquired_at",OffsetDateTime.class),rs.getObject("released_at",OffsetDateTime.class),rs.getString("release_reason"),rs.getLong("version")));}
    private ExecutionDispatchIntentV206 intent(String t,String id){return ExecutionDispatchIntentStore.mapOne(jdbc,json,t,id);}
    private void appendIntentEvent(String t,String intent,String assignment,String from,String to,String reason,Map<String,Object> evidence){jdbc.update("insert into execution_dispatch_intent_events_v206(tenant_id,event_id,intent_id,assignment_id,from_status,to_status,reason_code,actor_ref,evidence_json,occurred_at) values(:tenant,:event,:intent,:assignment,:from,:to,:reason,:actor,cast(:evidence as jsonb),now())",new MapSqlParameterSource("tenant",t).addValue("event","dispatch-intent-event-"+UUID.randomUUID()).addValue("intent",intent).addValue("assignment",assignment).addValue("from",from).addValue("to",to).addValue("reason",reason).addValue("actor",actor()).addValue("evidence",write(evidence)));}
    private Map<String,Object> readMap(String v){try{return blank(v)?Map.of():json.readValue(v,OBJECT_MAP);}catch(Exception e){return Map.of();}} private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException("JSON serialization failed",e);}}
    private void bind(String t){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);if(c!=null&&!"INSTANCE".equalsIgnoreCase(c.tenantId())&&!t.equals(c.tenantId()))throw new IllegalArgumentException("Tenant context mismatch for A0-R7 execution safety");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,t);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor());}
    private String actor(){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);return c==null||blank(c.actorId())?"a0-r7-execution-safety":c.actorId();}
    private String tenant(String v){return required(v,"tenantId");}private String required(String v,String f){if(blank(v))throw new IllegalArgumentException(f+" is required");return v.trim();}private String upper(String v){return required(v,"targetState").toUpperCase(Locale.ROOT).replace(' ','_');}private String trim(String v){return blank(v)?null:v.trim();}private boolean blank(String v){return v==null||v.isBlank();}private String str(Object v){return v==null?null:String.valueOf(v);}private String first(String a,String b){return !blank(a)?a:b;}
    private record StepSafety(String sideEffect,String writeSemantics){} private record AdapterSafety(String providerType,String adapterType,Map<String,Object> config){} private record AgentRuntime(String agentType,String gatewayNode,String sessionId,String siteId){}
}
