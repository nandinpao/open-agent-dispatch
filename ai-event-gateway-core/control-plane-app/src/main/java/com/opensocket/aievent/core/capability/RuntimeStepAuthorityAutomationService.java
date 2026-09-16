package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.math.BigDecimal;
import java.time.Duration;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 12 machine authority orchestrator.
 *
 * <p>Task provenance identifies the principal only; current IAM/RBAC is re-resolved server-side. For each RUNTIME READY Step, current server-side requester state is resolved, current APPROVED/REGISTERED
 * WHO CAN bindings are enumerated, RUNTIME WHO MAY is evaluated for every candidate, then RUNTIME WHO SHOULD
 * and RUNTIME HOW are produced. No Provider/Agent/Pool/Domain/transport is accepted as caller input.</p>
 */
@Service
public class RuntimeStepAuthorityAutomationService implements RuntimeStepAuthorityAutomationPort {
    private static final Set<String> POLICY_STATUSES=Set.of("DRAFT","ACTIVE","DISABLED","RETIRED");
    private static final Set<String> ACCESS_MODES=Set.of("READ","WRITE","EXECUTE");
    private static final TypeReference<Map<String,String>> STRING_MAP=new TypeReference<>(){};
    private static final TypeReference<Map<String,Object>> OBJECT_MAP=new TypeReference<>(){};
    private static final TypeReference<List<String>> STRING_LIST=new TypeReference<>(){};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DelegationGovernanceService governance;
    private final ProviderRoutingService routing;
    private final ExecutionAdapterService adapters;
    private final PlanAdmissionService planAdmission;

    public RuntimeStepAuthorityAutomationService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,
            DelegationGovernanceService governance,ProviderRoutingService routing,ExecutionAdapterService adapters,PlanAdmissionService planAdmission){
        this.jdbc=jdbc;this.json=json;this.governance=governance;this.routing=routing;this.adapters=adapters;this.planAdmission=planAdmission;
    }

    @Transactional(readOnly=true)
    public List<RuntimeStepAuthorityPolicy> listPolicies(String tenantId,String status,int limit){
        String t=tenant(tenantId);bind(t);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("limit",limit(limit));
        String w=" where tenant_id=:tenant";if(!blank(status)){w+=" and status=:status";p.addValue("status",status.trim().toUpperCase(Locale.ROOT));}
        return jdbc.query("select * from runtime_step_authority_policies"+w+" order by policy_id limit :limit",p,(rs,n)->policy(rs));
    }

    @Transactional
    public RuntimeStepAuthorityPolicy upsertPolicy(String tenantId,String pathPolicyId,RuntimeStepAuthorityPolicy req,String reason){
        String t=tenant(tenantId);bind(t);if(req==null)throw new IllegalArgumentException("Runtime Step Authority Policy body is required");
        String id=required(first(pathPolicyId,req.policyId()),"policyId");String status=normStatus(req.status());String access=normAccess(req.defaultAccessMode());
        Map<String,String> opModes=normalizeOperationModes(req.operationAccessModes());int max=Math.max(1,Math.min(req.maxCandidateBindings()<=0?200:req.maxCandidateBindings(),5000));
        String profile=required(req.routingProfileId(),"routingProfileId");Map<String,Object> rp=one("select status from routing_profiles where tenant_id=:tenant and profile_id=:id",t,profile);if(!"ACTIVE".equals(str(rp,"status"))&&"ACTIVE".equals(status))throw new IllegalArgumentException("Runtime Step Authority Policy requires an ACTIVE Routing Profile");
        RuntimeStepAuthorityPolicy old=findPolicy(t,id).orElse(null);if(old!=null&&blank(reason))throw new IllegalArgumentException("X-Change-Reason is required when changing a Runtime Step Authority Policy");
        if("ACTIVE".equals(status)){Integer c=jdbc.queryForObject("select count(*) from runtime_step_authority_policies where tenant_id=:tenant and status='ACTIVE' and policy_id<>:id",new MapSqlParameterSource("tenant",t).addValue("id",id),Integer.class);if(c!=null&&c>0)throw new IllegalArgumentException("Only one ACTIVE Runtime Step Authority Policy is allowed per Tenant");}
        int v=old==null?1:old.version()+1;OffsetDateTime now=OffsetDateTime.now(),created=old==null?now:old.createdAt();
        jdbc.update("""
          insert into runtime_step_authority_policies(tenant_id,policy_id,display_name,routing_profile_id,default_access_mode,operation_access_modes_json,max_candidate_bindings,automatic_attachment_enabled,status,version,created_at,updated_at)
          values(:tenant,:id,:name,:profile,:access,cast(:opModes as jsonb),:max,:auto,:status,:version,:created,:updated)
          on conflict(tenant_id,policy_id) do update set display_name=excluded.display_name,routing_profile_id=excluded.routing_profile_id,default_access_mode=excluded.default_access_mode,operation_access_modes_json=excluded.operation_access_modes_json,max_candidate_bindings=excluded.max_candidate_bindings,automatic_attachment_enabled=excluded.automatic_attachment_enabled,status=excluded.status,version=excluded.version,updated_at=excluded.updated_at
          """,new MapSqlParameterSource("tenant",t).addValue("id",id).addValue("name",required(req.displayName(),"displayName")).addValue("profile",profile).addValue("access",access).addValue("opModes",write(opModes)).addValue("max",max).addValue("auto",req.automaticAttachmentEnabled()).addValue("status",status).addValue("version",v).addValue("created",created).addValue("updated",now));
        RuntimeStepAuthorityPolicy saved=findPolicy(t,id).orElseThrow();appendPolicyVersion(saved,old==null?"INITIAL_CREATE":reason);return saved;
    }

    @Transactional(readOnly=true)
    public List<RuntimeStepAuthorityPolicyVersion> policyVersions(String tenantId,String policyId){
        String t=tenant(tenantId);bind(t);return jdbc.query("select * from runtime_step_authority_policy_versions where tenant_id=:tenant and policy_id=:id order by version desc",new MapSqlParameterSource("tenant",t).addValue("id",required(policyId,"policyId")),(rs,n)->new RuntimeStepAuthorityPolicyVersion(t,rs.getString("policy_id"),rs.getInt("version"),readObjectMap(rs.getString("snapshot_json")),rs.getString("change_reason"),rs.getString("actor_ref"),rs.getObject("created_at",OffsetDateTime.class)));
    }

    @Transactional(readOnly=true)
    public List<RuntimeStepAuthorityDecision> decisions(String tenantId,String runId,String stepId,int limit){
        String t=tenant(tenantId);bind(t);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("limit",limit(limit));String w=" where tenant_id=:tenant";
        if(!blank(runId)){w+=" and run_id=:run";p.addValue("run",runId.trim());}if(!blank(stepId)){w+=" and step_id=:step";p.addValue("step",stepId.trim());}
        return jdbc.query("select * from runtime_step_authority_decisions"+w+" order by decided_at desc,decision_id desc limit :limit",p,(rs,n)->decision(rs));
    }

    /** Machine/runtime entry point. It never accepts untrusted requester roles, Provider IDs or approval counts. */
    @Override @Transactional
    public RuntimeStepAuthorityAutomationResult prepare(String tenantId,String runId,String stepId){
        String t=tenant(tenantId);bind(t);StepContext step=loadStep(t,required(runId,"runId"),required(stepId,"stepId"));
        if(!"RUNTIME".equals(step.executionMode())||!"READY".equals(step.state()))return result(t,step,null,null,"NOT_READY",List.of("RUNTIME_READY_STEP_REQUIRED"),List.of(),null,null,0,0,0);
        RuntimeStepAuthorityPolicy policy=activePolicy(t).orElse(null);if(policy==null)return result(t,step,null,null,"POLICY_NOT_CONFIGURED",List.of("RUNTIME_STEP_AUTHORITY_POLICY_NOT_CONFIGURED"),List.of(),null,null,0,0,0);
        if(!policy.automaticAttachmentEnabled())return result(t,step,null,policy,"POLICY_NOT_CONFIGURED",List.of("AUTOMATIC_ATTACHMENT_DISABLED"),List.of(),null,null,0,0,0);
        RuntimeRequesterContext requester;
        try{requester=resolveRequester(t,step.taskRef(),step.runId());}catch(IllegalArgumentException ex){return result(t,step,null,policy,"REQUESTER_CONTEXT_INVALID",List.of(safe(ex.getMessage())),List.of(),null,null,0,0,0);}
        List<String> admittedBindingIds=planAdmission.admittedBindingIds(t,step.bindingAuthorizationEnvelopeId());
        List<BindingCandidate> candidates=whoCan(t,step.requirement()).stream().filter(c->admittedBindingIds.contains(c.bindingId())).toList();if(candidates.size()>policy.maxCandidateBindings())return result(t,step,requester,policy,"CANDIDATE_SET_TOO_LARGE",List.of("WHO_CAN_CANDIDATE_SET_EXCEEDS_POLICY"),List.of(),null,null,candidates.size(),0,0);
        if(candidates.isEmpty())return result(t,step,requester,policy,"NO_CANDIDATE",List.of("NO_APPROVED_REGISTERED_WHO_CAN_BINDING"),List.of(),null,null,0,0,0);
        String access=runtimeAccessForStep(step,policy);
        List<String> pass=new ArrayList<>();List<String> allAuth=new ArrayList<>();int waiting=0;
        // approvalCount is intentionally zero here: Phase 12 never trusts caller-supplied Human approval state.
        for(BindingCandidate c:candidates){ProviderMetrics metrics=latestMetrics(t,c.bindingId());DelegationAuthorizationRequest ar=new DelegationAuthorizationRequest(step.requirement(),c.bindingId(),requester.principalType(),requester.departmentId(),requester.groupIds(),requester.roleCodes(),access,requester.sensitivityLevel(),metrics.estimatedCost(),requester.delegationDepth(),requester.agentCalls(),metrics.p95LatencyMs(),0);DelegationAuthorizationDecision d=governance.evaluateRuntime(t,ar);allAuth.add(d.decisionId());if("PASS".equals(d.result()))pass.add(d.decisionId());else if("WAITING_APPROVAL".equals(d.result()))waiting++;}
        if(pass.isEmpty())return result(t,step,requester,policy,waiting>0?"WAITING_APPROVAL":"NO_CANDIDATE",waiting>0?List.of("WHO_MAY_REQUIRES_HUMAN_APPROVAL"):List.of("NO_WHO_MAY_PASS_CANDIDATE"),allAuth,null,null,candidates.size(),0,waiting);
        ProviderRoutingDecision rd=routing.evaluateRuntime(t,new ProviderRoutingPreviewRequest(step.requirement().capabilityCode(),step.requirement().operation(),policy.routingProfileId(),pass));
        if(!"SELECTED".equals(rd.result()))return result(t,step,requester,policy,"ROUTING_UNAVAILABLE",merge(List.of("WHO_SHOULD_"+rd.result()),rd.reasonCodes()),allAuth,rd.decisionId(),null,candidates.size(),pass.size(),waiting);
        ExecutionAdapterResolution er=adapters.resolveRuntime(t,new ExecutionAdapterResolutionRequest(rd.decisionId()));
        if(!"SELECTED".equals(er.result()))return result(t,step,requester,policy,"HOW_UNAVAILABLE",merge(List.of("HOW_"+er.result()),er.reasonCodes()),allAuth,rd.decisionId(),er.resolutionId(),candidates.size(),pass.size(),waiting);
        RuntimeStepAuthorityDecision evidence=persist(t,step,requester,policy,"AUTHORIZED",allAuth,rd.decisionId(),er.resolutionId(),candidates.size(),pass.size(),waiting,List.of("WHO_CAN_DISCOVERED_SERVER_SIDE","RUNTIME_WHO_MAY_PASS","RUNTIME_WHO_SHOULD_SELECTED","RUNTIME_HOW_SELECTED","NO_TARGET_PROVIDER_AGENT_POOL_OR_PROTOCOL_INPUT"));
        return new RuntimeStepAuthorityAutomationResult("AUTHORIZED",evidence,new PlanStepAuthorityRequest(passDecisionForSelected(rd,pass),rd.decisionId(),er.resolutionId()));
    }

    private String passDecisionForSelected(ProviderRoutingDecision rd,List<String> pass){
        if(rd.selectedBindingId()==null)throw new IllegalArgumentException("WHO SHOULD selected no Binding");
        return rd.candidates().stream().filter(c->rd.selectedBindingId().equals(c.bindingId())&&rd.selectedProviderId().equals(c.providerId())).map(ProviderRoutingCandidateScore::authorizationDecisionId).filter(pass::contains).findFirst().orElseThrow(()->new IllegalArgumentException("Selected WHO SHOULD candidate is not backed by a PASS WHO MAY decision"));
    }

    private RuntimeStepAuthorityAutomationResult result(String t,StepContext step,RuntimeRequesterContext requester,RuntimeStepAuthorityPolicy policy,String status,List<String> reasons,List<String> authIds,String routingId,String adapterId,int candidates,int pass,int waiting){
        RuntimeStepAuthorityDecision d=persist(t,step,requester,policy,status,authIds,routingId,adapterId,candidates,pass,waiting,reasons);return new RuntimeStepAuthorityAutomationResult(status,d,null);
    }

    private RuntimeStepAuthorityDecision persist(String t,StepContext s,RuntimeRequesterContext requester,RuntimeStepAuthorityPolicy policy,String result,List<String> authIds,String routingId,String adapterId,int candidates,int pass,int waiting,List<String> reasons){
        String id="runtime-step-authority-"+UUID.randomUUID();OffsetDateTime now=OffsetDateTime.now();String ptype=requester==null?"SYSTEM":requester.principalType(),pid=requester==null?"UNRESOLVED":requester.principalId(),policyId=policy==null?null:policy.policyId(),profile=policy==null?null:policy.routingProfileId();Integer pv=policy==null?null:policy.version();
        jdbc.update("""
          insert into runtime_step_authority_decisions(tenant_id,decision_id,run_id,step_id,attempt_generation,result,capability_code,operation,requester_principal_type,requester_principal_id,runtime_policy_id,runtime_policy_version,routing_profile_id,candidate_count,pass_count,waiting_approval_count,authorization_decision_ids_json,routing_decision_id,adapter_resolution_id,reason_codes_json,decided_at)
          values(:tenant,:id,:run,:step,:attempt,:result,:cap,:op,:ptype,:pid,:policy,:pv,:profile,:candidates,:pass,:waiting,cast(:auth as jsonb),:routing,:adapter,cast(:reasons as jsonb),:at)
          """,new MapSqlParameterSource("tenant",t).addValue("id",id).addValue("run",s.runId()).addValue("step",s.stepId()).addValue("attempt",s.attemptCount()).addValue("result",result).addValue("cap",s.requirement().capabilityCode()).addValue("op",s.requirement().operation()).addValue("ptype",ptype).addValue("pid",pid).addValue("policy",policyId).addValue("pv",pv).addValue("profile",profile).addValue("candidates",candidates).addValue("pass",pass).addValue("waiting",waiting).addValue("auth",write(authIds)).addValue("routing",routingId).addValue("adapter",adapterId).addValue("reasons",write(reasons)).addValue("at",now));
        return new RuntimeStepAuthorityDecision(id,t,s.runId(),s.stepId(),s.attemptCount(),result,s.requirement().capabilityCode(),s.requirement().operation(),ptype,pid,policyId,pv,profile,candidates,pass,waiting,authIds,routingId,adapterId,reasons,now);
    }

    private List<BindingCandidate> whoCan(String t,CapabilityRequirement req){
        return jdbc.query("""
          select b.binding_id,b.provider_id,p.provider_type,b.supported_operations_json
          from capability_bindings b join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
          where b.tenant_id=:tenant and b.capability_code=:cap and b.trust_status='APPROVED' and p.catalog_status='REGISTERED'
            and (b.stale_after is null or b.stale_after>now())
          order by b.binding_id
          """,new MapSqlParameterSource("tenant",t).addValue("cap",req.capabilityCode()),(rs,n)->new BindingCandidate(rs.getString("binding_id"),rs.getString("provider_id"),rs.getString("provider_type"),readStrings(rs.getString("supported_operations_json")))).stream().filter(c->c.operations().isEmpty()||c.operations().contains(req.operation().toUpperCase(Locale.ROOT))).toList();
    }

    private ProviderMetrics latestMetrics(String t,String binding){
        try{return jdbc.queryForObject("select estimated_cost,p95_latency_ms from provider_eligibility_observations where tenant_id=:tenant and binding_id=:binding order by observed_at desc,observation_id desc limit 1",new MapSqlParameterSource("tenant",t).addValue("binding",binding),(rs,n)->new ProviderMetrics(rs.getBigDecimal("estimated_cost"),(Long)rs.getObject("p95_latency_ms")));}catch(EmptyResultDataAccessException ex){return new ProviderMetrics(null,null);}
    }

    private RuntimeRequesterContext resolveRequester(String t,String taskRef,String runId){
        String task=normalizeTaskRef(taskRef);if(blank(task))throw new IllegalArgumentException("RUNTIME_TASK_REF_REQUIRED_FOR_REQUESTER_CONTEXT");Map<String,Object> row=one("select origin_principal_type,origin_principal_id,requester_department_id,requester_group_id,sensitivity_level,site_id,plant_id,hop_count from tasks where tenant_id=:tenant and task_id=:id",t,task);
        String rawType=str(row,"origin_principal_type"),pid=str(row,"origin_principal_id");if(blank(rawType)||blank(pid))throw new IllegalArgumentException("TASK_ORIGIN_PRINCIPAL_PROVENANCE_REQUIRED");String type=mapPrincipalType(rawType),dept=null;List<String> groups=List.of(),roles=List.of();
        if("HUMAN".equals(type)){validateHuman(t,pid);dept=currentPrimaryDepartment(t,pid);groups=currentGroups(t,pid);roles=currentRoles(t,"USER",pid,groups);}
        else if("SERVICE_ACCOUNT".equals(type)){Map<String,Object> sa=one("select status,owner_department_id from token_service_accounts where tenant_id=:tenant and service_account_id=:id",t,pid);if(!"ACTIVE".equals(str(sa,"status")))throw new IllegalArgumentException("SERVICE_ACCOUNT_NOT_ACTIVE");dept=str(sa,"owner_department_id");roles=currentRoles(t,"SERVICE_ACCOUNT",pid,List.of());}
        else if("AGENT".equals(type)){Map<String,Object> a=one("select approval_status,enabled,owner_department_id,owner_group_id from agent_profiles where tenant_id=:tenant and agent_id=:id",t,pid);if(!Boolean.TRUE.equals(a.get("enabled"))||!"APPROVED".equals(str(a,"approval_status")))throw new IllegalArgumentException("AGENT_NOT_CURRENTLY_APPROVED");dept=str(a,"owner_department_id");String g=str(a,"owner_group_id");groups=blank(g)?List.of():List.of(g);}
        else if(!"SYSTEM".equals(type))throw new IllegalArgumentException("UNSUPPORTED_RUNTIME_REQUESTER_PRINCIPAL_TYPE:"+rawType);
        Integer calls=jdbc.queryForObject("select count(*) from plan_execution_attempts where tenant_id=:tenant and run_id=:run",new MapSqlParameterSource("tenant",t).addValue("run",runId),Integer.class);int depth=row.get("hop_count") instanceof Number n?n.intValue():0;
        return new RuntimeRequesterContext(type,pid,dept,groups,roles,first(str(row,"sensitivity_level"),"INTERNAL"),str(row,"site_id"),str(row,"plant_id"),Math.max(0,depth),calls==null?0:calls);
    }

    private void validateHuman(String t,String user){Integer c=jdbc.queryForObject("select count(*) from iam_users u join org_tenant_memberships m on m.user_id=u.user_id and m.tenant_id=:tenant where u.user_id=:id and u.status='ACTIVE' and m.status='ACTIVE' and (m.expires_at is null or m.expires_at>now())",new MapSqlParameterSource("tenant",t).addValue("id",user),Integer.class);if(c==null||c==0)throw new IllegalArgumentException("HUMAN_REQUESTER_NOT_CURRENTLY_ACTIVE");}
    private String currentPrimaryDepartment(String t,String user){try{return jdbc.queryForObject("select department_id from org_department_memberships where tenant_id=:tenant and user_id=:id and status='ACTIVE' and is_primary=true and effective_at<=now() and (expires_at is null or expires_at>now()) limit 1",new MapSqlParameterSource("tenant",t).addValue("id",user),String.class);}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("HUMAN_REQUESTER_PRIMARY_DEPARTMENT_REQUIRED");}}
    private List<String> currentGroups(String t,String user){return jdbc.queryForList("select group_id from org_group_memberships where tenant_id=:tenant and user_id=:id and status='ACTIVE' and effective_at<=now() and (expires_at is null or expires_at>now()) order by group_id",new MapSqlParameterSource("tenant",t).addValue("id",user),String.class);}
    private List<String> currentRoles(String t,String principalType,String principalId,List<String> groups){LinkedHashSet<String> out=new LinkedHashSet<>();out.addAll(jdbc.queryForList("select distinct r.role_code from rbac_principal_role_bindings b join rbac_roles r on r.role_id=b.role_id where (b.tenant_id=:tenant or b.tenant_id is null) and b.principal_type=:ptype and b.principal_id=:pid and b.status='ACTIVE' and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now()) and r.status='ACTIVE'",new MapSqlParameterSource("tenant",t).addValue("ptype",principalType).addValue("pid",principalId),String.class));if("USER".equals(principalType)&&!groups.isEmpty())out.addAll(jdbc.queryForList("select distinct r.role_code from rbac_principal_role_bindings b join rbac_roles r on r.role_id=b.role_id where (b.tenant_id=:tenant or b.tenant_id is null) and b.principal_type='GROUP' and b.principal_id in (:groups) and b.status='ACTIVE' and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now()) and r.status='ACTIVE'",new MapSqlParameterSource("tenant",t).addValue("groups",groups),String.class));return List.copyOf(out);}

    private StepContext loadStep(String t,String run,String step){try{return jdbc.queryForObject("""
      select r.run_id,r.execution_mode,r.started_at,s.step_id,s.state,s.attempt_count,s.capability_requirement_json,s.binding_authorization_envelope_id,s.side_effect,s.write_semantics,p.task_ref
      from plan_execution_runs r join plan_execution_steps s on s.tenant_id=r.tenant_id and s.run_id=r.run_id
      join execution_plans p on p.tenant_id=r.tenant_id and p.plan_id=r.plan_id
      where r.tenant_id=:tenant and r.run_id=:run and s.step_id=:step
      """,new MapSqlParameterSource("tenant",t).addValue("run",run).addValue("step",step),(rs,n)->new StepContext(rs.getString("run_id"),rs.getString("step_id"),rs.getString("execution_mode"),rs.getString("state"),rs.getInt("attempt_count"),readRequirement(rs.getString("capability_requirement_json")),rs.getString("binding_authorization_envelope_id"),rs.getString("side_effect"),rs.getString("write_semantics"),rs.getString("task_ref"),rs.getObject("started_at",OffsetDateTime.class)));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("Plan execution Step not found");}}
    private Optional<RuntimeStepAuthorityPolicy> activePolicy(String t){try{return Optional.ofNullable(jdbc.queryForObject("select * from runtime_step_authority_policies where tenant_id=:tenant and status='ACTIVE'",new MapSqlParameterSource("tenant",t),(rs,n)->policy(rs)));}catch(EmptyResultDataAccessException ex){return Optional.empty();}}
    private Optional<RuntimeStepAuthorityPolicy> findPolicy(String t,String id){try{return Optional.ofNullable(jdbc.queryForObject("select * from runtime_step_authority_policies where tenant_id=:tenant and policy_id=:id",new MapSqlParameterSource("tenant",t).addValue("id",id),(rs,n)->policy(rs)));}catch(EmptyResultDataAccessException ex){return Optional.empty();}}
    private RuntimeStepAuthorityPolicy policy(java.sql.ResultSet rs)throws java.sql.SQLException{return new RuntimeStepAuthorityPolicy(rs.getString("tenant_id"),rs.getString("policy_id"),rs.getString("display_name"),rs.getString("routing_profile_id"),rs.getString("default_access_mode"),readStringMap(rs.getString("operation_access_modes_json")),rs.getInt("max_candidate_bindings"),rs.getBoolean("automatic_attachment_enabled"),rs.getString("status"),rs.getInt("version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));}
    private RuntimeStepAuthorityDecision decision(java.sql.ResultSet rs)throws java.sql.SQLException{return new RuntimeStepAuthorityDecision(rs.getString("decision_id"),rs.getString("tenant_id"),rs.getString("run_id"),rs.getString("step_id"),rs.getInt("attempt_generation"),rs.getString("result"),rs.getString("capability_code"),rs.getString("operation"),rs.getString("requester_principal_type"),rs.getString("requester_principal_id"),rs.getString("runtime_policy_id"),(Integer)rs.getObject("runtime_policy_version"),rs.getString("routing_profile_id"),rs.getInt("candidate_count"),rs.getInt("pass_count"),rs.getInt("waiting_approval_count"),readStrings(rs.getString("authorization_decision_ids_json")),rs.getString("routing_decision_id"),rs.getString("adapter_resolution_id"),readStrings(rs.getString("reason_codes_json")),rs.getObject("decided_at",OffsetDateTime.class));}
    private void appendPolicyVersion(RuntimeStepAuthorityPolicy p,String reason){jdbc.update("insert into runtime_step_authority_policy_versions(tenant_id,policy_id,version,snapshot_json,change_reason,actor_ref,created_at) values(:tenant,:id,:version,cast(:snapshot as jsonb),:reason,:actor,:at)",new MapSqlParameterSource("tenant",p.tenantId()).addValue("id",p.policyId()).addValue("version",p.version()).addValue("snapshot",write(p)).addValue("reason",required(reason,"reason")).addValue("actor",actor()).addValue("at",OffsetDateTime.now()));}

    private String runtimeAccessForStep(StepContext step,RuntimeStepAuthorityPolicy policy){String side=blank(step.sideEffect())?"WRITE":step.sideEffect().trim().toUpperCase(Locale.ROOT);if("WRITE".equals(side))return "WRITE";if("READ".equals(side))return "READ";return policy.operationAccessModes().getOrDefault(step.requirement().operation().toUpperCase(Locale.ROOT),policy.defaultAccessMode());}

    private String mapPrincipalType(String raw){return switch(raw.trim().toUpperCase(Locale.ROOT)){case "USER"->"HUMAN";case "SERVICE_ACCOUNT","INTEGRATION"->"SERVICE_ACCOUNT";case "AGENT","A2A_AGENT"->"AGENT";case "SYSTEM"->"SYSTEM";default->raw.trim().toUpperCase(Locale.ROOT);};}
    private String normalizeTaskRef(String v){if(blank(v))return null;String x=v.trim();if(x.startsWith("task://"))return x.substring(7);if(x.startsWith("task:"))return x.substring(5);return x;}
    private Map<String,String> normalizeOperationModes(Map<String,String> m){if(m==null)return Map.of();Map<String,String> out=new LinkedHashMap<>();m.forEach((k,v)->out.put(required(k,"operation").toUpperCase(Locale.ROOT),normAccess(v)));return Map.copyOf(out);}
    private String normAccess(String v){String x=blank(v)?"EXECUTE":v.trim().toUpperCase(Locale.ROOT);if(!ACCESS_MODES.contains(x))throw new IllegalArgumentException("accessMode must be READ, WRITE or EXECUTE");return x;}
    private String normStatus(String v){String x=blank(v)?"DRAFT":v.trim().toUpperCase(Locale.ROOT);if(!POLICY_STATUSES.contains(x))throw new IllegalArgumentException("Unsupported Runtime Step Authority Policy status: "+x);return x;}
    private Map<String,Object> one(String sql,String t,String id){try{return jdbc.queryForMap(sql,new MapSqlParameterSource("tenant",t).addValue("id",id));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("Referenced runtime authority resource not found");}}
    private void bind(String t){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);if(c!=null&&!"INSTANCE".equalsIgnoreCase(c.tenantId())&&!t.equals(c.tenantId()))throw new IllegalArgumentException("Tenant context mismatch for Runtime Step Authority");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,t);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor());}
    private String actor(){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);return c==null||blank(c.actorId())?"runtime-step-authority":c.actorId();}
    private String tenant(String v){return required(v,"tenantId");}private String required(String v,String f){if(blank(v))throw new IllegalArgumentException(f+" is required");return v.trim();}private boolean blank(String v){return v==null||v.isBlank();}private String first(String a,String b){return !blank(a)?a:b;}private String first(String a,String b,String c){return !blank(a)?a:!blank(b)?b:c;}private int limit(int v){return Math.max(1,Math.min(v<=0?200:v,500));}private String safe(String v){return blank(v)?"RUNTIME_AUTHORITY_CONTEXT_INVALID":v.replaceAll("[\\r\\n]+"," ").trim();}
    private List<String> merge(List<String>a,List<String>b){ArrayList<String>x=new ArrayList<>(a);if(b!=null)x.addAll(b);return List.copyOf(x);}private String str(Map<String,Object>m,String k){Object v=m.get(k);return v==null?null:String.valueOf(v);}private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException("Runtime Step Authority evidence cannot be serialized",e);}}private Map<String,String> readStringMap(String v){try{return blank(v)?Map.of():json.readValue(v,STRING_MAP);}catch(Exception e){throw new IllegalStateException("Runtime Step Authority operation map cannot be read",e);}}private Map<String,Object> readObjectMap(String v){try{return blank(v)?Map.of():json.readValue(v,OBJECT_MAP);}catch(Exception e){throw new IllegalStateException("Runtime Step Authority snapshot cannot be read",e);}}private List<String> readStrings(String v){try{return blank(v)?List.of():json.readValue(v,STRING_LIST);}catch(Exception e){throw new IllegalStateException("Runtime Step Authority list cannot be read",e);}}private CapabilityRequirement readRequirement(String v){try{return json.readValue(v,CapabilityRequirement.class);}catch(Exception e){throw new IllegalStateException("Step CapabilityRequirement cannot be read",e);}}

    private record StepContext(String runId,String stepId,String executionMode,String state,int attemptCount,CapabilityRequirement requirement,String bindingAuthorizationEnvelopeId,String sideEffect,String writeSemantics,String taskRef,OffsetDateTime runStartedAt){}
    private record BindingCandidate(String bindingId,String providerId,String providerType,List<String> operations){}
    private record ProviderMetrics(BigDecimal estimatedCost,Long p95LatencyMs){}
}
