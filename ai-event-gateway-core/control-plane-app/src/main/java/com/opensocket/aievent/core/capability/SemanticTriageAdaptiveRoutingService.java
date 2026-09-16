package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Stage 10 UNKNOWN-work authority.
 *
 * <p>Known work remains owned by deterministic Dispatch Flow/Flow Rule. This service is eligible only after
 * the canonical Flow resolver still returns NO_MATCH. Semantic output is WHAT-only evidence. Even in
 * CONTROLLED_LIVE it may create a planning request, never a Provider/Binding/Agent/Peer/Pool selection and
 * never an ExecutionAssignment or network send.</p>
 */
@Service
public class SemanticTriageAdaptiveRoutingService {
    private static final Set<String> MODES=Set.of("REPLAY","SHADOW","ADVISORY","CONTROLLED_LIVE");
    private static final Set<String> STATUSES=Set.of("DRAFT","ACTIVE","DISABLED","RETIRED");
    private static final TypeReference<List<CapabilityRequirement>> REQUIREMENTS=new TypeReference<>(){};
    private static final TypeReference<List<String>> STRINGS=new TypeReference<>(){};
    private static final TypeReference<Map<String,Object>> MAP=new TypeReference<>(){};
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TaskRepository tasks;
    private final FlowRuleRoutingService flows;
    private final SemanticTriageService triage;
    private final ObjectProvider<TriageAgentPort> triageAgents;
    private final ExecutionPlanService plans;

    public SemanticTriageAdaptiveRoutingService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,TaskRepository tasks,
            FlowRuleRoutingService flows,SemanticTriageService triage,ObjectProvider<TriageAgentPort> triageAgents,
            ExecutionPlanService plans){this.jdbc=jdbc;this.json=json;this.tasks=tasks;this.flows=flows;this.triage=triage;this.triageAgents=triageAgents;this.plans=plans;}

    @Transactional(readOnly=true)
    public List<SemanticTriageRuntimePolicy> policies(String tenantId){String t=tenant(tenantId);bind(t);return jdbc.query("select * from semantic_triage_runtime_policies where tenant_id=:tenant order by policy_id",new MapSqlParameterSource("tenant",t),new PolicyMapper());}

    @Transactional
    public SemanticTriageRuntimePolicy upsertPolicy(String tenantId,String pathPolicyId,SemanticTriageRuntimePolicy request,String changeReason){
        String t=tenant(tenantId);bind(t);if(request==null)throw new IllegalArgumentException("Runtime Triage Policy body is required");
        String id=required(first(pathPolicyId,request.policyId()),"policyId"), mode=upper(request.rolloutMode()), status=upper(first(request.status(),"DRAFT"));
        if(!MODES.contains(mode))throw new IllegalArgumentException("Unsupported rolloutMode: "+mode);if(!STATUSES.contains(status))throw new IllegalArgumentException("Unsupported status: "+status);
        double sample=request.sampleRate();if(sample<0||sample>1)throw new IllegalArgumentException("sampleRate must be between 0 and 1");
        if("CONTROLLED_LIVE".equals(mode)&&!request.controlledLiveApproved())throw new IllegalArgumentException("CONTROLLED_LIVE requires controlledLiveApproved=true");
        SemanticTriageRuntimePolicy old=findPolicy(t,id).orElse(null);if(old!=null&&blank(changeReason))throw new IllegalArgumentException("X-Change-Reason is required when changing an existing Runtime Triage Policy");
        if(old!=null&&!validRolloutTransition(old.rolloutMode(),mode))throw new IllegalArgumentException("Rollout promotion must advance one step at a time: REPLAY -> SHADOW -> ADVISORY -> CONTROLLED_LIVE; rollback is allowed");
        if("ACTIVE".equals(status)){Integer n=jdbc.queryForObject("select count(*) from semantic_triage_runtime_policies where tenant_id=:tenant and status='ACTIVE' and policy_id<>:id",new MapSqlParameterSource("tenant",t).addValue("id",id),Integer.class);if(n!=null&&n>0)throw new IllegalArgumentException("Only one ACTIVE Runtime Triage Policy is allowed per Tenant");}
        int version=old==null?1:old.version()+1;OffsetDateTime now=OffsetDateTime.now(),created=old==null?now:old.createdAt();
        jdbc.update("""
          insert into semantic_triage_runtime_policies(tenant_id,policy_id,rollout_mode,sample_rate,controlled_live_approved,model_profile_ref,prompt_profile_ref,status,version,created_at,updated_at)
          values(:tenant,:id,:mode,:sample,:approved,:model,:prompt,:status,:version,:created,:updated)
          on conflict(tenant_id,policy_id) do update set rollout_mode=excluded.rollout_mode,sample_rate=excluded.sample_rate,controlled_live_approved=excluded.controlled_live_approved,model_profile_ref=excluded.model_profile_ref,prompt_profile_ref=excluded.prompt_profile_ref,status=excluded.status,version=excluded.version,updated_at=excluded.updated_at
          """,new MapSqlParameterSource("tenant",t).addValue("id",id).addValue("mode",mode).addValue("sample",sample).addValue("approved",request.controlledLiveApproved()).addValue("model",trim(request.modelProfileRef())).addValue("prompt",trim(request.promptProfileRef())).addValue("status",status).addValue("version",version).addValue("created",created).addValue("updated",now));
        SemanticTriageRuntimePolicy saved=findPolicy(t,id).orElseThrow();jdbc.update("insert into semantic_triage_runtime_policy_versions(tenant_id,policy_id,version,snapshot_json,change_reason,actor_ref,created_at) values(:tenant,:id,:version,cast(:snapshot as jsonb),:reason,:actor,:at)",new MapSqlParameterSource("tenant",t).addValue("id",id).addValue("version",version).addValue("snapshot",write(saved)).addValue("reason",old==null?"INITIAL_CREATE":changeReason.trim()).addValue("actor","STAGE10_ADMIN").addValue("at",now));return saved;
    }

    @Transactional(readOnly=true)
    public List<SemanticTriageRuntimeDecision> decisions(String tenantId,String taskId,int limit){String t=tenant(tenantId);bind(t);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("limit",Math.max(1,Math.min(limit,500)));String w=" where tenant_id=:tenant";if(!blank(taskId)){w+=" and task_id=:task";p.addValue("task",taskId.trim());}return jdbc.query("select * from semantic_triage_runtime_decisions"+w+" order by decided_at desc limit :limit",p,new DecisionMapper());}

    /** Explicit replay or runtime evaluation. Flow match is always rechecked before semantic reasoning. */
    @Transactional
    public SemanticTriageRuntimeDecision evaluateTask(String tenantId,String taskId,String triggerType){
        String t=tenant(tenantId);bind(t);String trigger=upper(first(triggerType,"REPLAY"));if(!Set.of("REPLAY","RUNTIME").contains(trigger))throw new IllegalArgumentException("triggerType must be REPLAY or RUNTIME");
        TaskRecord task=tasks.findByTenantAndId(t,required(taskId,"taskId")).orElseThrow(()->new IllegalArgumentException("Task not found: "+taskId));
        FlowRuleRoutingPlan flow=flows.resolve(task);
        flows.recordAuthoritativeDecision(task,flow);
        SemanticTriageRuntimePolicy policy=activePolicy(t).orElse(null);String mode=policy==null?"REPLAY":policy.rolloutMode();
        if(flow!=null&&flow.isAmbiguous())return persist(t,task,trigger,"RESOLUTION_ERROR",mode,policy,null,null,"FLOW_CONFIGURATION_AMBIGUOUS",null,List.of("FLOW_RULE_SAME_PRIORITY_AMBIGUOUS","FAIL_CLOSED_NO_SEMANTIC_TRIAGE"),Map.of("flowReason",safe(flow.getReason())));
        if(flow!=null&&flow.isMatched())return persist(t,task,trigger,"MATCHED",mode,policy,null,null,"FLOW_MATCHED_SKIPPED",null,List.of("DETERMINISTIC_FLOW_RULE_MATCHED","SEMANTIC_TRIAGE_NOT_INVOKED"),Map.of("flowId",safe(flow.getFlowId()),"ruleId",safe(flow.getRuleId())));
        String flowReason=flow==null?"FLOW_RESOLUTION_RETURNED_NULL":safe(flow.getReason());
        if(flow==null||flowReason.contains("DB lookup failed")||flowReason.contains("tenantId is required")||flowReason.contains("sourceSystem is required"))return persist(t,task,trigger,"RESOLUTION_ERROR",mode,policy,null,null,"FLOW_RESOLUTION_ERROR_SKIPPED",null,List.of("FLOW_RESOLUTION_ERROR","FAIL_CLOSED_NO_SEMANTIC_TRIAGE"),Map.of("flowReason",flowReason));
        // This is the Stage 10 authority boundary: semantic reasoning is reachable only after confirmed deterministic NO_MATCH.
        if(policy==null)return persist(t,task,trigger,"NO_MATCH","REPLAY",null,null,null,"TRIAGE_UNAVAILABLE",null,List.of("RUNTIME_TRIAGE_POLICY_NOT_CONFIGURED","NO_EXECUTION_AUTHORITY"),Map.of("flowReason",flowReason));
        if("RUNTIME".equals(trigger)&&"REPLAY".equals(mode))return persist(t,task,trigger,"NO_MATCH",mode,policy,null,null,"NO_ACTION",null,List.of("REPLAY_MODE_REQUIRES_EXPLICIT_EVALUATION","NO_LIVE_TRIAGE"),Map.of("flowReason",flowReason));
        if(!sampled(task.getTaskId(),policy.sampleRate()))return persist(t,task,trigger,"NO_MATCH",mode,policy,null,null,"SAMPLE_SKIPPED",null,List.of("DETERMINISTIC_SAMPLE_EXCLUDED","NO_SEMANTIC_INVOCATION"),Map.of("sampleRate",policy.sampleRate()));

        TriageDecision semantic=triage.findLatestAcceptedDecisionForTask(t,task.getTaskId()).orElse(null);
        if(semantic==null){
            Map<String,Object> input=new LinkedHashMap<>();input.put("sourceSystem",safe(task.getSourceSystem()));input.put("eventType",safe(task.getEventType()));input.put("errorCode",safe(task.getErrorCode()));input.put("taskTypeCode",safe(task.getTaskTypeCode()));
            String problem=first(task.getDescription(),"Unknown work from "+safe(task.getSourceSystem())+" eventType="+safe(task.getEventType())+" errorCode="+safe(task.getErrorCode()));
            // serviceCode intentionally null: a Flow NO_MATCH must never be converted into the old Service-Code fast path.
            TriageDecision preview=triage.resolvePreview(t,new TriagePreviewRequest(task.getTaskId(),null,problem,List.of("task:"+task.getTaskId()),input,null));
            if(!"TRIAGE_REQUIRED".equals(preview.result()))return persist(t,task,trigger,"NO_MATCH",mode,policy,preview,null,"NO_ACTION",null,List.of("UNEXPECTED_NON_TRIAGE_RESULT_AFTER_FLOW_NO_MATCH","NO_EXECUTION_AUTHORITY"),Map.of());
            TriageAgentPort agent=triageAgents.getIfAvailable();if(agent==null)return persist(t,task,trigger,"NO_MATCH",mode,policy,preview,null,"TRIAGE_UNAVAILABLE",null,List.of("TRIAGE_AGENT_NOT_CONFIGURED","FAIL_CLOSED","MANUAL_PROPOSAL_MAY_BE_SUBMITTED_AND_REEVALUATED","NO_EXECUTION_AUTHORITY"),Map.of());
            TriageRequest request=triage.findRequest(t,preview.requestId()).orElseThrow(()->new IllegalStateException("Persisted Triage request missing: "+preview.requestId()));
            TriageProposal proposal=agent.propose(request);if(proposal==null)return persist(t,task,trigger,"NO_MATCH",mode,policy,preview,null,"TRIAGE_UNAVAILABLE",null,List.of("TRIAGE_AGENT_RETURNED_NO_PROPOSAL","FAIL_CLOSED"),Map.of());
            semantic=triage.submitProposal(t,preview.requestId(),proposal);
        }
        boolean accepted="CAPABILITY_REQUIREMENTS_PROPOSED".equals(semantic.result())&&!semantic.requiresHumanReview()&&!semantic.acceptedRequirements().isEmpty();
        if(!accepted)return persist(t,task,trigger,"NO_MATCH",mode,policy,semantic,null,"HUMAN_REVIEW_REQUIRED",null,List.of("SEMANTIC_WHAT_NOT_AUTO_ACCEPTABLE","NO_PROVIDER_OR_EXECUTION_AUTHORITY"),Map.of("semanticResult",semantic.result()));
        if("REPLAY".equals(mode))return persist(t,task,trigger,"NO_MATCH",mode,policy,semantic,null,"NO_ACTION",null,List.of("REPLAY_EVIDENCE_ONLY","CANONICAL_WHAT_VALIDATED","NO_EXECUTION_AUTHORITY"),Map.of());
        if("SHADOW".equals(mode))return persist(t,task,trigger,"NO_MATCH",mode,policy,semantic,null,"SHADOW_ONLY",null,List.of("SHADOW_EVIDENCE_ONLY","CANONICAL_WHAT_VALIDATED","NO_EXECUTION_AUTHORITY"),Map.of());
        if("ADVISORY".equals(mode))return persist(t,task,trigger,"NO_MATCH",mode,policy,semantic,null,"ADVISORY_AVAILABLE",null,List.of("ADVISORY_ONLY","HUMAN_OR_WORKFLOW_MUST_ACCEPT_NEXT_ACTION","NO_EXECUTION_AUTHORITY"),Map.of());
        if(!policy.controlledLiveApproved())return persist(t,task,trigger,"NO_MATCH",mode,policy,semantic,null,"HUMAN_REVIEW_REQUIRED",null,List.of("CONTROLLED_LIVE_NOT_EXPLICITLY_APPROVED","FAIL_CLOSED"),Map.of());
        String classification=semantic.classification()==null?null:semantic.classification().classificationCode();
        ExecutionPlanDecision planning=plans.resolvePreview(t,new ExecutionPlanPreviewRequest(task.getTaskId(),semantic.decisionId(),classification,List.of(),List.of("task:"+task.getTaskId())));
        return persist(t,task,trigger,"NO_MATCH",mode,policy,semantic,planning,"PLANNING_REQUEST_CREATED",planning.requestId(),List.of("CONTROLLED_LIVE_SEMANTIC_WHAT_ACCEPTED","PLANNING_REQUEST_CREATED","PLANNER_OR_VALIDATED_PLAN_STILL_REQUIRED","WHO_CAN_WHO_MAY_WHO_SHOULD_HOW_NOT_RUN_BY_TRIAGE","NO_EXECUTION_AUTHORITY"),Map.of("planningDecisionId",planning.decisionId(),"planningResult",planning.result()));
    }

    @Transactional
    public int enqueueRuntimeNoMatchCandidates(String tenantId,int limit){String t=tenant(tenantId);bind(t);SemanticTriageRuntimePolicy p=activePolicy(t).orElse(null);if(p==null||"REPLAY".equals(p.rolloutMode()))return 0;List<String> ids=jdbc.queryForList("select task_id from tasks where tenant_id=:tenant and routing_path in ('FLOW_RULE_NO_MATCH_TRIAGE','FLOW_RULE_REQUIRED_BLOCKED') and terminal_at is null order by updated_at asc limit :limit",new MapSqlParameterSource("tenant",t).addValue("limit",Math.max(1,Math.min(limit,500))),String.class);int n=0;for(String id:ids)n+=jdbc.update("insert into semantic_triage_runtime_work_items(tenant_id,task_id,status,next_attempt_at,created_at,updated_at) values(:tenant,:task,'PENDING',now(),now(),now()) on conflict(tenant_id,task_id) do nothing",new MapSqlParameterSource("tenant",t).addValue("task",id));return n;}

    @Transactional
    public List<String> claimDue(String tenantId,String workerId,int limit){String t=tenant(tenantId);bind(t);return jdbc.queryForList("""
      with due as (select task_id from semantic_triage_runtime_work_items where tenant_id=:tenant and status in ('PENDING','RETRY') and next_attempt_at<=now() and (claim_until is null or claim_until<now()) order by next_attempt_at,task_id limit :limit for update skip locked)
      update semantic_triage_runtime_work_items w set status='CLAIMED',claimed_by=:worker,claim_until=now()+interval '60 seconds',attempt_count=attempt_count+1,updated_at=now() from due where w.tenant_id=:tenant and w.task_id=due.task_id returning w.task_id
      """,new MapSqlParameterSource("tenant",t).addValue("worker",workerId).addValue("limit",Math.max(1,Math.min(limit,100))),String.class);}

    @Transactional public void completeWork(String tenantId,String taskId){String t=tenant(tenantId);bind(t);jdbc.update("update semantic_triage_runtime_work_items set status='COMPLETED',claimed_by=null,claim_until=null,last_error=null,updated_at=now() where tenant_id=:tenant and task_id=:task",new MapSqlParameterSource("tenant",t).addValue("task",taskId));}
    @Transactional public void failWork(String tenantId,String taskId,String error){String t=tenant(tenantId);bind(t);jdbc.update("update semantic_triage_runtime_work_items set status='RETRY',claimed_by=null,claim_until=null,last_error=:error,next_attempt_at=now()+least(interval '5 minutes',interval '10 seconds'*greatest(attempt_count,1)),updated_at=now() where tenant_id=:tenant and task_id=:task",new MapSqlParameterSource("tenant",t).addValue("task",taskId).addValue("error",safe(error)));}

    private SemanticTriageRuntimeDecision persist(String t,TaskRecord task,String trigger,String flowResult,String mode,SemanticTriageRuntimePolicy policy,TriageDecision semantic,ExecutionPlanDecision planning,String action,String planningRequest,List<String> reasons,Map<String,Object> evidence){String id="triage-runtime-"+UUID.randomUUID();OffsetDateTime at=OffsetDateTime.now();List<CapabilityRequirement> accepted=semantic==null?List.of():semantic.acceptedRequirements();String classification=semantic==null||semantic.classification()==null?null:semantic.classification().classificationCode();jdbc.update("""
      insert into semantic_triage_runtime_decisions(tenant_id,runtime_decision_id,task_id,trigger_type,flow_match_result,rollout_mode,runtime_policy_id,runtime_policy_version,triage_request_id,semantic_decision_id,semantic_result,action,classification_code,accepted_requirements_json,planning_request_id,model_profile_ref,prompt_profile_ref,execution_side_effect_allowed,reason_codes_json,evidence_json,decided_at)
      values(:tenant,:id,:task,:trigger,:flow,:mode,:policy,:version,:request,:semantic,:result,:action,:classification,cast(:requirements as jsonb),:planning,:model,:prompt,false,cast(:reasons as jsonb),cast(:evidence as jsonb),:at)
      """,new MapSqlParameterSource("tenant",t).addValue("id",id).addValue("task",task.getTaskId()).addValue("trigger",trigger).addValue("flow",flowResult).addValue("mode",mode).addValue("policy",policy==null?null:policy.policyId()).addValue("version",policy==null?null:policy.version()).addValue("request",semantic==null?null:semantic.requestId()).addValue("semantic",semantic==null?null:semantic.decisionId()).addValue("result",semantic==null?null:semantic.result()).addValue("action",action).addValue("classification",classification).addValue("requirements",write(accepted)).addValue("planning",planningRequest).addValue("model",policy==null?null:policy.modelProfileRef()).addValue("prompt",policy==null?null:policy.promptProfileRef()).addValue("reasons",write(reasons)).addValue("evidence",write(evidence)).addValue("at",at));return new SemanticTriageRuntimeDecision(id,t,task.getTaskId(),trigger,flowResult,mode,policy==null?null:policy.policyId(),policy==null?null:policy.version(),semantic==null?null:semantic.requestId(),semantic==null?null:semantic.decisionId(),semantic==null?null:semantic.result(),action,classification,accepted,planningRequest,policy==null?null:policy.modelProfileRef(),policy==null?null:policy.promptProfileRef(),false,reasons,evidence,at);}
    private Optional<SemanticTriageRuntimePolicy> activePolicy(String t){try{return Optional.ofNullable(jdbc.queryForObject("select * from semantic_triage_runtime_policies where tenant_id=:tenant and status='ACTIVE'",new MapSqlParameterSource("tenant",t),new PolicyMapper()));}catch(EmptyResultDataAccessException ex){return Optional.empty();}}
    private Optional<SemanticTriageRuntimePolicy> findPolicy(String t,String id){try{return Optional.ofNullable(jdbc.queryForObject("select * from semantic_triage_runtime_policies where tenant_id=:tenant and policy_id=:id",new MapSqlParameterSource("tenant",t).addValue("id",id),new PolicyMapper()));}catch(EmptyResultDataAccessException ex){return Optional.empty();}}
    private boolean validRolloutTransition(String from,String to){if(from==null||from.equals(to))return true;List<String> order=List.of("REPLAY","SHADOW","ADVISORY","CONTROLLED_LIVE");int a=order.indexOf(from),b=order.indexOf(to);return b<=a||b==a+1;}
    private boolean sampled(String id,double rate){if(rate>=1)return true;if(rate<=0)return false;long h=Integer.toUnsignedLong(id.hashCode());return (h%10000)<Math.round(rate*10000d);}
    private String tenant(String v){String t=required(v,"tenantId");return t;}
    private void bind(String t){bindTenantContext(t,"STAGE10_ADAPTIVE_TRIAGE");}
    private void bindTenantContext(String tenantId,String backgroundActor){
        IamTenantExecutionContext current=IamTenantContextHolder.current().orElse(null);
        String actor=backgroundActor;
        if(current!=null){
            if(!tenantId.equals(current.tenantId()))throw new IllegalStateException("TENANT_CONTEXT_MISMATCH");
            actor=current.actorId();
        }else{
            if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("TENANT_TRANSACTION_REQUIRED");
            IamTenantContextHolder.Scope scope=IamTenantContextHolder.open(new IamTenantExecutionContext(tenantId,backgroundActor));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){scope.close();}});
        }
        MapSqlParameterSource context=new MapSqlParameterSource("tenant",tenantId).addValue("actor",actor);
        jdbc.queryForObject("select set_config('app.current_tenant_id',:tenant,true) || ':' || set_config('app.current_actor_id',:actor,true)",context,String.class);
    }
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalArgumentException("JSON serialization failed",ex);}}
    private <T>T read(String v,TypeReference<T> type,T fallback){try{return blank(v)?fallback:json.readValue(v,type);}catch(Exception ex){return fallback;}}
    private static boolean blank(String v){return v==null||v.isBlank();}private static String trim(String v){return blank(v)?null:v.trim();}private static String safe(String v){return v==null?"":v;}private static String upper(String v){return blank(v)?null:v.trim().toUpperCase(Locale.ROOT);}private static String first(String a,String b){return blank(a)?b:a;}private static String required(String v,String n){if(blank(v))throw new IllegalArgumentException(n+" is required");return v.trim();}
    private final class PolicyMapper implements RowMapper<SemanticTriageRuntimePolicy>{public SemanticTriageRuntimePolicy mapRow(ResultSet r,int n)throws SQLException{return new SemanticTriageRuntimePolicy(r.getString("tenant_id"),r.getString("policy_id"),r.getString("rollout_mode"),r.getDouble("sample_rate"),r.getBoolean("controlled_live_approved"),r.getString("model_profile_ref"),r.getString("prompt_profile_ref"),r.getString("status"),r.getInt("version"),r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class));}}
    private final class DecisionMapper implements RowMapper<SemanticTriageRuntimeDecision>{public SemanticTriageRuntimeDecision mapRow(ResultSet r,int n)throws SQLException{return new SemanticTriageRuntimeDecision(r.getString("runtime_decision_id"),r.getString("tenant_id"),r.getString("task_id"),r.getString("trigger_type"),r.getString("flow_match_result"),r.getString("rollout_mode"),r.getString("runtime_policy_id"),(Integer)r.getObject("runtime_policy_version"),r.getString("triage_request_id"),r.getString("semantic_decision_id"),r.getString("semantic_result"),r.getString("action"),r.getString("classification_code"),read(r.getString("accepted_requirements_json"),REQUIREMENTS,List.of()),r.getString("planning_request_id"),r.getString("model_profile_ref"),r.getString("prompt_profile_ref"),r.getBoolean("execution_side_effect_allowed"),read(r.getString("reason_codes_json"),STRINGS,List.of()),read(r.getString("evidence_json"),MAP,Map.of()),r.getObject("decided_at",OffsetDateTime.class));}}
}
