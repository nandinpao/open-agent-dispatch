package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 7 Execution Plan / Multi-Capability Planning authority.
 *
 * <p>A Planner can only propose Canonical Capability requirements and dependencies. OpenDispatch validates
 * capability existence/operation, graph shape, cycle/depth/concurrency and tenant Plan Policy. A semantically
 * validated Plan is still not Provider authorization: every Step must later traverse WHO CAN -> WHO MAY ->
 * WHO SHOULD -> HOW independently.</p>
 */
@Service
public class ExecutionPlanService {
    private static final Pattern STEP_ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_.:-]{0,159}$");
    private static final Pattern CLASSIFICATION_CODE = Pattern.compile("^[A-Z][A-Z0-9_:-]{2,159}$");
    private static final Set<String> POLICY_STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED", "RETIRED");
    private static final Set<String> PROPOSER_TYPES = Set.of("PLANNER_AGENT", "HUMAN_ANALYST", "SYSTEM_IMPORT");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<CapabilityRequirement>> REQUIREMENTS = new TypeReference<>() {};
    private static final TypeReference<List<ExecutionPlanStep>> STEPS = new TypeReference<>() {};
    private static final TypeReference<Map<String,Object>> OBJECT_MAP = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CanonicalCapabilityManagementService capabilities;

    public ExecutionPlanService(NamedParameterJdbcTemplate jdbc, ObjectMapper json,
                                CanonicalCapabilityManagementService capabilities) {
        this.jdbc = jdbc;
        this.json = json;
        this.capabilities = capabilities;
    }

    @Transactional(readOnly = true)
    public List<ExecutionPlanPolicy> listPolicies(String tenantId, String status, int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("limit",normalizeLimit(limit));
        String where=" where tenant_id=:tenant";
        if(!blank(status)){ where += " and status=:status"; p.addValue("status",normalizePolicyStatus(status)); }
        return jdbc.query("select * from execution_plan_policies"+where+" order by policy_id asc limit :limit",p,new PolicyMapper());
    }

    @Transactional(readOnly = true)
    public Optional<ExecutionPlanPolicy> findPolicy(String tenantId,String policyId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        try { return Optional.ofNullable(jdbc.queryForObject("select * from execution_plan_policies where tenant_id=:tenant and policy_id=:id",
                new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(policyId,"policyId")),new PolicyMapper())); }
        catch(EmptyResultDataAccessException ex){ return Optional.empty(); }
    }

    @Transactional
    public ExecutionPlanPolicy upsertPolicy(String tenantId,String pathPolicyId,ExecutionPlanPolicy request,String changeReason) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        if(request==null) throw new IllegalArgumentException("Execution Plan Policy request body is required");
        String id=requireNonBlank(firstNonBlank(pathPolicyId,request.policyId()),"policyId");
        if(!blank(request.policyId())&&!id.equals(request.policyId().trim())) throw new IllegalArgumentException("policyId in request body must match path");
        String status=normalizePolicyStatus(request.status());
        int maxSteps=between(request.maxSteps(),1,500,"maxSteps");
        int maxDepth=between(request.maxPlanDepth(),1,100,"maxPlanDepth");
        int maxBranches=between(request.maxConcurrentBranches(),1,500,"maxConcurrentBranches");
        int maxInvocations=between(request.maxCapabilityInvocations(),1,1000,"maxCapabilityInvocations");
        Long token=positiveNullable(request.maxTokenBudget(),"maxTokenBudget");
        Double cost=nonNegativeNullable(request.maxEstimatedCost(),"maxEstimatedCost");
        Long time=positiveNullable(request.maxExecutionTimeSeconds(),"maxExecutionTimeSeconds");
        if("ACTIVE".equals(status)) {
            Integer active=jdbc.queryForObject("select count(*) from execution_plan_policies where tenant_id=:tenant and status='ACTIVE' and policy_id<>:id",
                    new MapSqlParameterSource("tenant",tenant).addValue("id",id),Integer.class);
            if(active!=null&&active>0) throw new IllegalArgumentException("Only one ACTIVE Execution Plan Policy is allowed per Tenant");
        }
        ExecutionPlanPolicy existing=findPolicy(tenant,id).orElse(null);
        if(existing!=null&&blank(changeReason)) throw new IllegalArgumentException("X-Change-Reason is required when changing an existing Execution Plan Policy");
        int version=existing==null?1:existing.version()+1; OffsetDateTime now=OffsetDateTime.now(); OffsetDateTime created=existing==null?now:existing.createdAt();
        jdbc.update("""
            insert into execution_plan_policies(tenant_id,policy_id,display_name,max_steps,max_plan_depth,max_concurrent_branches,
              max_capability_invocations,max_token_budget,max_estimated_cost,max_execution_time_seconds,require_human_review_on_plan_change,
              status,version,created_at,updated_at)
            values(:tenant,:id,:name,:steps,:depth,:branches,:invocations,:token,:cost,:time,:human,:status,:version,:created,:updated)
            on conflict(tenant_id,policy_id) do update set display_name=excluded.display_name,max_steps=excluded.max_steps,
              max_plan_depth=excluded.max_plan_depth,max_concurrent_branches=excluded.max_concurrent_branches,
              max_capability_invocations=excluded.max_capability_invocations,max_token_budget=excluded.max_token_budget,
              max_estimated_cost=excluded.max_estimated_cost,max_execution_time_seconds=excluded.max_execution_time_seconds,
              require_human_review_on_plan_change=excluded.require_human_review_on_plan_change,status=excluded.status,
              version=excluded.version,updated_at=excluded.updated_at
            """,new MapSqlParameterSource("tenant",tenant).addValue("id",id).addValue("name",requireNonBlank(request.displayName(),"displayName"))
                .addValue("steps",maxSteps).addValue("depth",maxDepth).addValue("branches",maxBranches).addValue("invocations",maxInvocations)
                .addValue("token",token).addValue("cost",cost).addValue("time",time).addValue("human",request.requireHumanReviewOnPlanChange())
                .addValue("status",status).addValue("version",version).addValue("created",created).addValue("updated",now));
        ExecutionPlanPolicy saved=findPolicy(tenant,id).orElseThrow(); appendPolicyVersion(saved,existing==null?"INITIAL_CREATE":requireNonBlank(changeReason,"changeReason")); return saved;
    }

    @Transactional(readOnly = true)
    public List<ExecutionPlanPolicyVersion> policyVersions(String tenantId,String policyId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        return jdbc.query("select * from execution_plan_policy_versions where tenant_id=:tenant and policy_id=:id order by version desc",
                new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(policyId,"policyId")),
                (rs,n)->new ExecutionPlanPolicyVersion(rs.getString("tenant_id"),rs.getString("policy_id"),rs.getInt("version"),readMap(rs.getString("snapshot_json")),rs.getString("change_reason"),rs.getString("actor_ref"),rs.getObject("created_at",OffsetDateTime.class)));
    }

    /** Creates planning evidence only. A Planner Agent is not invoked by this Admin preview. */
    @Transactional
    public ExecutionPlanDecision resolvePreview(String tenantId,ExecutionPlanPreviewRequest request) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        if(request==null) throw new IllegalArgumentException("Execution Plan preview request body is required");
        List<String> initialErrors=new ArrayList<>();
        List<CapabilityRequirement> initial;
        TriageSourceEvidence source=blank(request.sourceTriageDecisionId())?null:requireTriageSourceEvidence(tenant,request.sourceTriageDecisionId());
        if(source!=null) {
            if(request.initialRequirements()!=null&&!request.initialRequirements().isEmpty()) {
                List<CapabilityRequirement> supplied=normalizeRequirements(tenant,request.initialRequirements(),false,initialErrors);
                if(!supplied.equals(source.requirements())) throw new IllegalArgumentException("initialRequirements must match the authoritative source Triage Decision; omit them to inherit WHAT evidence");
            }
            initial=normalizeRequirements(tenant,source.requirements(),false,initialErrors);
        } else {
            initial=normalizeRequirements(tenant,request.initialRequirements(),false,initialErrors);
        }
        if(!initialErrors.isEmpty()) throw new IllegalArgumentException("Planning input is not valid Canonical WHAT: "+String.join(",",initialErrors));
        String requestId="plan-request-"+UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now();
        jdbc.update("""
            insert into execution_plan_requests(tenant_id,request_id,task_ref,source_triage_decision_id,classification_code,initial_requirements_json,context_refs_json,status,created_at)
            values(:tenant,:id,:task,:triage,:classification,cast(:requirements as jsonb),cast(:context as jsonb),'PLANNING_REQUIRED',:at)
            """,new MapSqlParameterSource("tenant",tenant).addValue("id",requestId).addValue("task",trim(request.taskRef()))
                .addValue("triage",trim(request.sourceTriageDecisionId())).addValue("classification",normalizeOptionalClassification(request.classificationCode()))
                .addValue("requirements",write(initial)).addValue("context",write(cleanStrings(request.contextRefs()))).addValue("at",now));
        Optional<ExecutionPlanPolicy> policy=activePolicy(tenant);
        if(policy.isEmpty()) return persistDecision(tenant,requestId,null,"PREVIEW","PLAN_POLICY_NOT_CONFIGURED",null,null,null,null,List.of("PLAN_POLICY_NOT_CONFIGURED","PLANNER_NOT_INVOKED","NO_PROVIDER_ROUTING_OR_EXECUTION_OCCURRED"),true);
        return persistDecision(tenant,requestId,null,"PREVIEW","PLANNING_REQUIRED",null,null,policy.get(),null,List.of("PLANNING_REQUIRED","PLANNER_MAY_PROPOSE_CAPABILITIES_AND_DEPENDENCIES_ONLY","PLANNER_NOT_INVOKED_IN_ADMIN_PREVIEW","NO_PROVIDER_ROUTING_OR_EXECUTION_OCCURRED"),false);
    }

    /** Validates a Planner/Human proposal and may create the first immutable semantic plan revision. */
    @Transactional
    public ExecutionPlanDecision submitProposal(String tenantId,String requestId,ExecutionPlanProposal proposal) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant); ExecutionPlanRequest req=requireRequest(tenant,requestId);
        if(proposal==null) throw new IllegalArgumentException("Execution Plan proposal body is required");
        String proposerType=normalizeProposerType(proposal.proposerType()); String proposalId=blank(proposal.proposalId())?"plan-proposal-"+UUID.randomUUID():proposal.proposalId().trim();
        List<ExecutionPlanStep> raw=proposal.steps()==null?List.of():proposal.steps(); OffsetDateTime at=proposal.proposedAt()==null?OffsetDateTime.now():proposal.proposedAt();
        persistProposal(tenant,proposalId,requestId,proposerType,proposal.proposerRef(),raw,proposal.rationale(),at);
        Optional<ExecutionPlanPolicy> policy=activePolicy(tenant);
        if(policy.isEmpty()) return persistDecision(tenant,requestId,proposalId,"PREVIEW","PLAN_POLICY_NOT_CONFIGURED",null,null,null,null,List.of("PLAN_POLICY_NOT_CONFIGURED","PLANNER_PROPOSAL_NOT_EXECUTABLE_AUTHORITY"),true);
        Validation v=validateSteps(tenant,raw,policy.get());
        if(!v.capabilityGaps().isEmpty()) return persistDecision(tenant,requestId,proposalId,"PREVIEW","CAPABILITY_GAP",null,null,policy.get(),v,List.of("CAPABILITY_GAP","UNKNOWN_OR_INACTIVE_CANONICAL_CAPABILITY","PLANNER_PROPOSAL_NOT_EXECUTABLE_AUTHORITY"),true);
        if(!v.errors().isEmpty()) return persistDecision(tenant,requestId,proposalId,"PREVIEW","PLAN_INVALID",null,null,policy.get(),v,merge(v.errors(),List.of("PLAN_INVALID","FAIL_CLOSED_NO_PROVIDER_ROUTING_OR_EXECUTION")),true);
        String planId="execution-plan-"+UUID.randomUUID(); int revision=1;
        ExecutionPlanBudget budget=budget(policy.get());
        insertPlan(tenant,planId,req,policy.get(),revision,"SEMANTICALLY_VALIDATED");
        insertRevision(tenant,planId,revision,proposalId,v.steps(),budget,"INITIAL_VALIDATED_PLAN");
        jdbc.update("update execution_plan_requests set status='PLAN_VALIDATED' where tenant_id=:tenant and request_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("id",requestId));
        return persistDecision(tenant,requestId,proposalId,"PREVIEW","PLAN_VALIDATED",planId,revision,policy.get(),v,List.of("PLAN_VALIDATED","SEMANTIC_VALIDATION_ONLY","EACH_STEP_MUST_LATER_PASS_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW","NO_PROVIDER_AGENT_POOL_OR_PROTOCOL_SELECTED"),false);
    }

    /**
     * Phase 11 runtime materialization of one already-governed semantic Fast Path template.
     * This skips Planner invocation only. The resulting Plan is still WHAT-only and every Step must later
     * pass WHO CAN -> WHO MAY -> WHO SHOULD -> HOW before Phase 8 may dispatch it.
     */
    @Transactional
    public ExecutionPlanDecision materializeFastPathRuntime(String tenantId,String taskRef,String classificationCode,
            List<CapabilityRequirement> signatureRequirements,List<String> contextRefs,Map<String,Object> planTemplate,
            String patternId,int patternVersion) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        if(planTemplate==null||planTemplate.isEmpty()) throw new IllegalArgumentException("Fast Path planTemplate is required");
        Object rawSteps=planTemplate.get("steps");
        if(rawSteps==null) throw new IllegalArgumentException("Fast Path planTemplate.steps is required");
        List<ExecutionPlanStep> steps;
        try { steps=json.readValue(json.writeValueAsString(rawSteps),STEPS); }
        catch(Exception ex){ throw new IllegalArgumentException("Fast Path planTemplate.steps is not a valid semantic Plan",ex); }
        Optional<ExecutionPlanPolicy> policy=activePolicy(tenant);
        if(policy.isEmpty()) throw new IllegalArgumentException("PLAN_POLICY_NOT_CONFIGURED");
        Validation v=validateSteps(tenant,steps,policy.get());
        if(!v.capabilityGaps().isEmpty()) throw new IllegalArgumentException("FAST_PATH_CAPABILITY_GAP:"+String.join(",",v.capabilityGaps()));
        if(!v.errors().isEmpty()) throw new IllegalArgumentException("FAST_PATH_PLAN_INVALID:"+String.join(",",v.errors()));
        List<CapabilityRequirement> signature=normalizeRequirements(tenant,signatureRequirements,false,new ArrayList<>());
        if(signature.isEmpty()) throw new IllegalArgumentException("Fast Path signature requirements must not be empty");
        String requestId="plan-request-fastpath-"+UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now();
        jdbc.update("""
            insert into execution_plan_requests(tenant_id,request_id,task_ref,source_triage_decision_id,classification_code,initial_requirements_json,context_refs_json,status,created_at)
            values(:tenant,:id,:task,null,:classification,cast(:requirements as jsonb),cast(:context as jsonb),'PLAN_VALIDATED',:at)
            """,new MapSqlParameterSource("tenant",tenant).addValue("id",requestId).addValue("task",trim(taskRef))
              .addValue("classification",normalizeOptionalClassification(classificationCode)).addValue("requirements",write(signature))
              .addValue("context",write(cleanStrings(contextRefs))).addValue("at",now));
        String proposalId="plan-proposal-fastpath-"+UUID.randomUUID();
        persistProposal(tenant,proposalId,requestId,"SYSTEM_IMPORT","routing-pattern:"+patternId+":v"+patternVersion,v.steps(),
                "Phase 11 certified Fast Path semantic template; Planner was intentionally skipped",now);
        ExecutionPlanRequest req=new ExecutionPlanRequest(requestId,tenant,trim(taskRef),null,normalizeOptionalClassification(classificationCode),signature,cleanStrings(contextRefs),"PLAN_VALIDATED",now);
        String planId="execution-plan-fastpath-"+UUID.randomUUID(); int revision=1; ExecutionPlanBudget budget=budget(policy.get());
        insertPlan(tenant,planId,req,policy.get(),revision,"SEMANTICALLY_VALIDATED");
        insertRevision(tenant,planId,revision,proposalId,v.steps(),budget,"PHASE11_CERTIFIED_FAST_PATH_RUNTIME_MATERIALIZATION");
        return persistDecision(tenant,requestId,proposalId,"RUNTIME","PLAN_VALIDATED",planId,revision,policy.get(),v,
                List.of("PLAN_VALIDATED","FAST_PATH_RUNTIME_MATERIALIZED","TRIAGE_AND_PLANNER_SKIPPED_BY_CERTIFIED_PATTERN","EACH_STEP_MUST_REEVALUATE_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW","NO_PROVIDER_AGENT_POOL_OR_PROTOCOL_PINNED"),false);
    }

    /** Proposes a new plan revision. If policy requires Human review, no revision is created automatically. */
    @Transactional
    public ExecutionPlanDecision amendPlan(String tenantId,String planId,ExecutionPlanAmendmentRequest amendment) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant); ExecutionPlan current=requirePlan(tenant,planId);
        if(amendment==null) throw new IllegalArgumentException("Execution Plan amendment body is required");
        String reason=requireNonBlank(amendment.changeReason(),"changeReason"); String type=normalizeProposerType(amendment.proposerType());
        String proposalId="plan-proposal-"+UUID.randomUUID(); OffsetDateTime at=OffsetDateTime.now();
        persistProposal(tenant,proposalId,current.requestId(),type,amendment.proposerRef(),amendment.steps(),amendment.rationale(),at);
        Optional<ExecutionPlanPolicy> p=activePolicy(tenant);
        if(p.isEmpty()) return persistDecision(tenant,current.requestId(),proposalId,"PREVIEW","PLAN_POLICY_NOT_CONFIGURED",null,null,null,null,List.of("PLAN_POLICY_NOT_CONFIGURED","PLAN_AMENDMENT_NOT_APPLIED"),true);
        Validation v=validateSteps(tenant,amendment.steps(),p.get());
        if(!v.capabilityGaps().isEmpty()) return persistDecision(tenant,current.requestId(),proposalId,"PREVIEW","CAPABILITY_GAP",planId,current.currentRevision(),p.get(),v,List.of("CAPABILITY_GAP","PLAN_AMENDMENT_NOT_APPLIED"),true);
        if(!v.errors().isEmpty()) return persistDecision(tenant,current.requestId(),proposalId,"PREVIEW","PLAN_INVALID",planId,current.currentRevision(),p.get(),v,merge(v.errors(),List.of("PLAN_AMENDMENT_NOT_APPLIED")),true);
        if(p.get().requireHumanReviewOnPlanChange()) return persistDecision(tenant,current.requestId(),proposalId,"PREVIEW","HUMAN_REVIEW_REQUIRED",planId,current.currentRevision(),p.get(),v,List.of("PLAN_CHANGE_REQUIRES_HUMAN_REVIEW","PLAN_AMENDMENT_NOT_APPLIED","PLANNER_CANNOT_APPROVE_ITS_OWN_PLAN_CHANGE"),true);
        int next=current.currentRevision()+1; ExecutionPlanBudget budget=budget(p.get());
        insertRevision(tenant,planId,next,proposalId,v.steps(),budget,reason);
        jdbc.update("update execution_plans set policy_id=:policy,policy_version=:version,current_revision=:revision,status='SEMANTICALLY_VALIDATED',updated_at=:at where tenant_id=:tenant and plan_id=:plan",
                new MapSqlParameterSource("tenant",tenant).addValue("plan",planId).addValue("policy",p.get().policyId()).addValue("version",p.get().version()).addValue("revision",next).addValue("at",OffsetDateTime.now()));
        return persistDecision(tenant,current.requestId(),proposalId,"PREVIEW","PLAN_VALIDATED",planId,next,p.get(),v,List.of("PLAN_REVISION_VALIDATED","SEMANTIC_VALIDATION_ONLY","EACH_STEP_MUST_LATER_PASS_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW","NO_PROVIDER_AGENT_POOL_OR_PROTOCOL_SELECTED"),false);
    }

    @Transactional(readOnly = true)
    public List<ExecutionPlan> listPlans(String tenantId,String status,String afterPlanId,int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("limit",normalizeLimit(limit)); String where=" where tenant_id=:tenant";
        if(!blank(status)){ where += " and status=:status"; p.addValue("status",status.trim().toUpperCase(Locale.ROOT)); }
        if(!blank(afterPlanId)){ where += " and plan_id>:after"; p.addValue("after",afterPlanId.trim()); }
        List<ExecutionPlan> out=new ArrayList<>();
        for(PlanHeader h:jdbc.query("select * from execution_plans"+where+" order by plan_id asc limit :limit",p,new PlanHeaderMapper())) out.add(toPlan(h));
        return List.copyOf(out);
    }

    @Transactional(readOnly = true)
    public Optional<ExecutionPlan> findPlan(String tenantId,String planId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        try { return Optional.of(toPlan(jdbc.queryForObject("select * from execution_plans where tenant_id=:tenant and plan_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(planId,"planId")),new PlanHeaderMapper()))); }
        catch(EmptyResultDataAccessException ex){ return Optional.empty(); }
    }

    @Transactional(readOnly = true)
    public List<ExecutionPlanRevision> revisions(String tenantId,String planId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant); requirePlan(tenant,planId);
        return jdbc.query("select * from execution_plan_revisions where tenant_id=:tenant and plan_id=:plan order by revision desc",
                new MapSqlParameterSource("tenant",tenant).addValue("plan",planId),(rs,n)->new ExecutionPlanRevision(tenant,planId,rs.getInt("revision"),rs.getString("proposal_id"),readMap(rs.getString("snapshot_json")),loadSteps(tenant,planId,rs.getInt("revision")),rs.getString("change_reason"),rs.getString("actor_ref"),rs.getObject("created_at",OffsetDateTime.class)));
    }

    @Transactional(readOnly = true)
    public List<ExecutionPlanRequest> listRequests(String tenantId,String status,int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant); MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("limit",normalizeLimit(limit)); String w=" where tenant_id=:tenant";
        if(!blank(status)){w+=" and status=:status";p.addValue("status",status.trim().toUpperCase(Locale.ROOT));}
        return jdbc.query("select * from execution_plan_requests"+w+" order by created_at desc limit :limit",p,new RequestMapper());
    }

    @Transactional(readOnly = true)
    public List<ExecutionPlanProposal> listProposals(String tenantId,String requestId,int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant); MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("limit",normalizeLimit(limit)); String w=" where tenant_id=:tenant";
        if(!blank(requestId)){w+=" and request_id=:request";p.addValue("request",requestId.trim());}
        return jdbc.query("select * from execution_plan_proposals"+w+" order by proposed_at desc limit :limit",p,new ProposalMapper());
    }

    @Transactional(readOnly = true)
    public List<ExecutionPlanDecision> listDecisions(String tenantId,String requestId,int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant); MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("limit",normalizeLimit(limit)); String w=" where tenant_id=:tenant";
        if(!blank(requestId)){w+=" and request_id=:request";p.addValue("request",requestId.trim());}
        return jdbc.query("select * from execution_plan_decisions"+w+" order by decided_at desc limit :limit",p,new DecisionMapper());
    }

    private Validation validateSteps(String tenant,List<ExecutionPlanStep> proposed,ExecutionPlanPolicy policy) {
        List<String> errors=new ArrayList<>(); List<String> gaps=new ArrayList<>();
        if(proposed==null||proposed.isEmpty()) errors.add("PLAN_HAS_NO_STEPS");
        if(proposed!=null&&proposed.size()>policy.maxSteps()) errors.add("MAX_STEPS_EXCEEDED");
        if(proposed!=null&&proposed.size()>policy.maxCapabilityInvocations()) errors.add("MAX_CAPABILITY_INVOCATIONS_EXCEEDED");
        LinkedHashMap<String,ExecutionPlanStep> byId=new LinkedHashMap<>();
        if(proposed!=null) for(ExecutionPlanStep raw:proposed) {
            if(raw==null){errors.add("NULL_PLAN_STEP");continue;}
            String id=normalizeStepId(raw.stepId()); if(byId.containsKey(id)){errors.add("DUPLICATE_STEP_ID:"+id);continue;}
            CapabilityRequirement req=normalizeRequirement(tenant,raw.requiredCapability(),gaps,errors);
            List<String> deps=cleanStepIds(raw.dependsOn()); if(deps.contains(id)) errors.add("SELF_DEPENDENCY:"+id);
            byId.put(id,new ExecutionPlanStep(id,req,deps,trim(raw.purpose()),raw.required(),raw.sequenceHint(),raw.sideEffect(),raw.writeSemantics(),raw.compensationBindingId(),raw.maxBindingFallback()));
        }
        for(ExecutionPlanStep s:byId.values()) for(String dep:s.dependsOn()) if(!byId.containsKey(dep)) errors.add("UNKNOWN_DEPENDENCY:"+s.stepId()+"->"+dep);
        GraphMetrics metrics=graphMetrics(byId,errors);
        if(metrics.maxDepth()>policy.maxPlanDepth()) errors.add("MAX_PLAN_DEPTH_EXCEEDED");
        if(metrics.maxConcurrentBranches()>policy.maxConcurrentBranches()) errors.add("MAX_CONCURRENT_BRANCHES_EXCEEDED");
        return new Validation(List.copyOf(byId.values()),distinct(errors),distinct(gaps),metrics.maxDepth(),metrics.maxConcurrentBranches());
    }

    private CapabilityRequirement normalizeRequirement(String tenant,CapabilityRequirement raw,List<String> gaps,List<String> errors) {
        if(raw==null){errors.add("MISSING_CAPABILITY_REQUIREMENT");return null;}
        String code=requireNonBlank(raw.capabilityCode(),"capabilityCode").trim().toLowerCase(Locale.ROOT); CapabilityDefinition d=capabilities.find(tenant,code).orElse(null);
        if(d==null||!"ACTIVE".equals(d.status())) { gaps.add(code); return new CapabilityRequirement(code,normalizeUpper(raw.operation()),raw.inputContext(),raw.resourceConstraints(),trim(raw.dataClassification()),trim(raw.requiredAssurance()),raw.deadline(),trim(raw.qualityPreference())); }
        String op=normalizeUpper(raw.operation());
        if(blank(op)&&d.operations().size()==1) op=d.operations().getFirst();
        if(blank(op)&&d.operations().size()>1) errors.add("OPERATION_REQUIRED:"+code);
        if(!blank(op)&&!d.operations().contains(op)) errors.add("UNSUPPORTED_CAPABILITY_OPERATION:"+code+":"+op);
        return new CapabilityRequirement(code,op,raw.inputContext(),raw.resourceConstraints(),trim(raw.dataClassification()),trim(raw.requiredAssurance()),raw.deadline(),trim(raw.qualityPreference()));
    }

    private List<CapabilityRequirement> normalizeRequirements(String tenant,List<CapabilityRequirement> values,boolean require,List<String> errors) {
        if(values==null||values.isEmpty()) { if(require) errors.add("INITIAL_REQUIREMENTS_REQUIRED"); return List.of(); }
        List<CapabilityRequirement> out=new ArrayList<>(); List<String> gaps=new ArrayList<>(); for(CapabilityRequirement r:values){CapabilityRequirement n=normalizeRequirement(tenant,r,gaps,errors);if(n!=null)out.add(n);} if(!gaps.isEmpty())errors.add("INITIAL_CAPABILITY_GAP"); return List.copyOf(out);
    }

    private GraphMetrics graphMetrics(Map<String,ExecutionPlanStep> byId,List<String> errors) {
        if(byId.isEmpty()) return new GraphMetrics(0,0);
        Map<String,Integer> indegree=new HashMap<>(); Map<String,List<String>> outgoing=new HashMap<>();
        for(String id:byId.keySet()){indegree.put(id,0);outgoing.put(id,new ArrayList<>());}
        for(ExecutionPlanStep s:byId.values()) for(String dep:s.dependsOn()) if(byId.containsKey(dep)){indegree.put(s.stepId(),indegree.get(s.stepId())+1);outgoing.get(dep).add(s.stepId());}
        ArrayDeque<String> q=new ArrayDeque<>(); Map<String,Integer> depth=new HashMap<>(); for(var e:indegree.entrySet()) if(e.getValue()==0){q.add(e.getKey());depth.put(e.getKey(),1);}
        int visited=0,maxDepth=0; Map<Integer,Integer> width=new HashMap<>();
        while(!q.isEmpty()){String id=q.remove();visited++;int d=depth.getOrDefault(id,1);maxDepth=Math.max(maxDepth,d);width.put(d,width.getOrDefault(d,0)+1);for(String n:outgoing.get(id)){depth.put(n,Math.max(depth.getOrDefault(n,1),d+1));int v=indegree.get(n)-1;indegree.put(n,v);if(v==0)q.add(n);}}
        if(visited!=byId.size()) errors.add("PLAN_DEPENDENCY_CYCLE"); int maxBranches=0; for(int n:width.values())maxBranches=Math.max(maxBranches,n); return new GraphMetrics(maxDepth,maxBranches);
    }

    private void insertPlan(String tenant,String planId,ExecutionPlanRequest req,ExecutionPlanPolicy policy,int revision,String status) {
        OffsetDateTime now=OffsetDateTime.now(); jdbc.update("""
          insert into execution_plans(tenant_id,plan_id,request_id,task_ref,source_triage_decision_id,classification_code,policy_id,policy_version,status,current_revision,created_at,updated_at)
          values(:tenant,:plan,:request,:task,:triage,:classification,:policy,:policyVersion,:status,:revision,:at,:at)
          """,new MapSqlParameterSource("tenant",tenant).addValue("plan",planId).addValue("request",req.requestId()).addValue("task",req.taskRef()).addValue("triage",req.sourceTriageDecisionId()).addValue("classification",req.classificationCode()).addValue("policy",policy.policyId()).addValue("policyVersion",policy.version()).addValue("status",status).addValue("revision",revision).addValue("at",now));
    }

    private void insertRevision(String tenant,String planId,int revision,String proposalId,List<ExecutionPlanStep> steps,ExecutionPlanBudget budget,String reason) {
        OffsetDateTime now=OffsetDateTime.now(); Map<String,Object> snapshot=new LinkedHashMap<>(); snapshot.put("budget",budget); snapshot.put("steps",steps); snapshot.put("semanticAuthority","PLAN_STEPS_ARE_WHAT_ONLY"); snapshot.put("executionBoundary","EACH_STEP_MUST_PASS_WHO_CAN_WHO_MAY_WHO_SHOULD_HOW");
        jdbc.update("insert into execution_plan_revisions(tenant_id,plan_id,revision,proposal_id,snapshot_json,change_reason,actor_ref,created_at) values(:tenant,:plan,:revision,:proposal,cast(:snapshot as jsonb),:reason,:actor,:at)",new MapSqlParameterSource("tenant",tenant).addValue("plan",planId).addValue("revision",revision).addValue("proposal",proposalId).addValue("snapshot",write(snapshot)).addValue("reason",reason).addValue("actor",actorRef()).addValue("at",now));
        for(ExecutionPlanStep s:steps) jdbc.update("""
          insert into execution_plan_revision_steps(tenant_id,plan_id,revision,step_id,capability_requirement_json,depends_on_json,purpose,required,sequence_hint,side_effect,write_semantics,compensation_binding_id,max_binding_fallback,created_at)
          values(:tenant,:plan,:revision,:step,cast(:requirement as jsonb),cast(:depends as jsonb),:purpose,:required,:sequence,:sideEffect,:writeSemantics,:compensationBinding,:maxFallback,:at)
          """,new MapSqlParameterSource("tenant",tenant).addValue("plan",planId).addValue("revision",revision).addValue("step",s.stepId()).addValue("requirement",write(s.requiredCapability())).addValue("depends",write(s.dependsOn())).addValue("purpose",s.purpose()).addValue("required",s.required()).addValue("sequence",s.sequenceHint()).addValue("sideEffect",s.sideEffect()).addValue("writeSemantics",s.writeSemantics()).addValue("compensationBinding",s.compensationBindingId()).addValue("maxFallback",s.maxBindingFallback()).addValue("at",now));
    }

    private void persistProposal(String tenant,String proposalId,String requestId,String type,String ref,List<ExecutionPlanStep> steps,String rationale,OffsetDateTime at) {
        jdbc.update("insert into execution_plan_proposals(tenant_id,proposal_id,request_id,proposer_type,proposer_ref,steps_json,rationale,proposed_at) values(:tenant,:proposal,:request,:type,:ref,cast(:steps as jsonb),:rationale,:at)",new MapSqlParameterSource("tenant",tenant).addValue("proposal",proposalId).addValue("request",requestId).addValue("type",type).addValue("ref",trim(ref)).addValue("steps",write(steps==null?List.of():steps)).addValue("rationale",trim(rationale)).addValue("at",at));
    }

    private ExecutionPlanDecision persistDecision(String tenant,String requestId,String proposalId,String mode,String result,String planId,Integer revision,ExecutionPlanPolicy policy,Validation v,List<String> reasons,boolean human) {
        String id="plan-decision-"+UUID.randomUUID(); OffsetDateTime at=OffsetDateTime.now(); Integer depth=v==null?null:v.maxDepth(); Integer branches=v==null?null:v.maxConcurrentBranches();
        jdbc.update("""
          insert into execution_plan_decisions(tenant_id,decision_id,request_id,proposal_id,decision_mode,result,plan_id,plan_revision,policy_id,policy_version,max_depth_observed,max_concurrent_branches_observed,reason_codes_json,requires_human_review,decided_at)
          values(:tenant,:id,:request,:proposal,:mode,:result,:plan,:revision,:policy,:policyVersion,:depth,:branches,cast(:reasons as jsonb),:human,:at)
          """,new MapSqlParameterSource("tenant",tenant).addValue("id",id).addValue("request",requestId).addValue("proposal",proposalId).addValue("mode",mode).addValue("result",result).addValue("plan",planId).addValue("revision",revision).addValue("policy",policy==null?null:policy.policyId()).addValue("policyVersion",policy==null?null:policy.version()).addValue("depth",depth).addValue("branches",branches).addValue("reasons",write(reasons==null?List.of():reasons)).addValue("human",human).addValue("at",at));
        return new ExecutionPlanDecision(id,tenant,requestId,proposalId,mode,result,planId,revision,policy==null?null:policy.policyId(),policy==null?null:policy.version(),depth,branches,reasons,human,at);
    }

    private void appendPolicyVersion(ExecutionPlanPolicy p,String reason) {
        jdbc.update("insert into execution_plan_policy_versions(tenant_id,policy_id,version,snapshot_json,change_reason,actor_ref,created_at) values(:tenant,:id,:version,cast(:snapshot as jsonb),:reason,:actor,:at)",new MapSqlParameterSource("tenant",p.tenantId()).addValue("id",p.policyId()).addValue("version",p.version()).addValue("snapshot",write(p)).addValue("reason",reason).addValue("actor",actorRef()).addValue("at",OffsetDateTime.now()));
    }

    private TriageSourceEvidence requireTriageSourceEvidence(String tenant,String decisionId) {
        try {
            return jdbc.queryForObject("select result,accepted_requirements_json from semantic_triage_decisions where tenant_id=:tenant and decision_id=:id",
                    new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(decisionId,"sourceTriageDecisionId")),
                    (rs,n)->{
                        String result=rs.getString("result");
                        if(!Set.of("KNOWN_FAST_PATH","CAPABILITY_REQUIREMENTS_PROPOSED").contains(result)) throw new IllegalArgumentException("sourceTriageDecisionId must reference authoritative Canonical WHAT evidence, not "+result);
                        List<CapabilityRequirement> requirements=readRequirements(rs.getString("accepted_requirements_json"));
                        if(requirements.isEmpty()) throw new IllegalArgumentException("source Triage Decision contains no accepted Capability requirements");
                        return new TriageSourceEvidence(result,requirements);
                    });
        } catch(EmptyResultDataAccessException ex) { throw new IllegalArgumentException("sourceTriageDecisionId was not found in the active Tenant"); }
    }

    private Optional<ExecutionPlanPolicy> activePolicy(String tenant) {
        try { return Optional.ofNullable(jdbc.queryForObject("select * from execution_plan_policies where tenant_id=:tenant and status='ACTIVE'",new MapSqlParameterSource("tenant",tenant),new PolicyMapper())); }
        catch(EmptyResultDataAccessException ex){ return Optional.empty(); }
    }

    private ExecutionPlanRequest requireRequest(String tenant,String requestId) {
        try { return jdbc.queryForObject("select * from execution_plan_requests where tenant_id=:tenant and request_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(requestId,"requestId")),new RequestMapper()); }
        catch(EmptyResultDataAccessException ex){ throw new IllegalArgumentException("Execution Plan Request not found: "+requestId); }
    }

    private ExecutionPlan requirePlan(String tenant,String planId) { return findPlan(tenant,planId).orElseThrow(()->new IllegalArgumentException("Execution Plan not found: "+planId)); }

    private ExecutionPlan toPlan(PlanHeader h) {
        List<ExecutionPlanStep> steps=loadSteps(h.tenantId(),h.planId(),h.currentRevision()); Map<String,Object> snap=revisionSnapshot(h.tenantId(),h.planId(),h.currentRevision()); ExecutionPlanBudget budget=readBudget(snap.get("budget"));
        return new ExecutionPlan(h.planId(),h.tenantId(),h.requestId(),h.taskRef(),h.sourceTriageDecisionId(),h.classificationCode(),h.policyId(),h.policyVersion(),h.status(),h.currentRevision(),budget,steps,h.createdAt(),h.updatedAt());
    }

    private Map<String,Object> revisionSnapshot(String tenant,String plan,int revision) {
        try { String s=jdbc.queryForObject("select snapshot_json::text from execution_plan_revisions where tenant_id=:tenant and plan_id=:plan and revision=:revision",new MapSqlParameterSource("tenant",tenant).addValue("plan",plan).addValue("revision",revision),String.class); return readMap(s); }
        catch(EmptyResultDataAccessException ex){ return Map.of(); }
    }

    private List<ExecutionPlanStep> loadSteps(String tenant,String plan,int revision) {
        return jdbc.query("select * from execution_plan_revision_steps where tenant_id=:tenant and plan_id=:plan and revision=:revision order by coalesce(sequence_hint,2147483647),step_id",new MapSqlParameterSource("tenant",tenant).addValue("plan",plan).addValue("revision",revision),(rs,n)->new ExecutionPlanStep(rs.getString("step_id"),readRequirement(rs.getString("capability_requirement_json")),readStrings(rs.getString("depends_on_json")),rs.getString("purpose"),rs.getBoolean("required"),(Integer)rs.getObject("sequence_hint"),rs.getString("side_effect"),rs.getString("write_semantics"),rs.getString("compensation_binding_id"),rs.getInt("max_binding_fallback")));
    }

    private ExecutionPlanBudget readBudget(Object raw) { try { if(raw==null)return null; return json.convertValue(raw,ExecutionPlanBudget.class); } catch(Exception ex){ throw new IllegalStateException("Execution Plan budget cannot be read",ex); } }
    private ExecutionPlanBudget budget(ExecutionPlanPolicy p){ return new ExecutionPlanBudget(p.maxSteps(),p.maxPlanDepth(),p.maxConcurrentBranches(),p.maxCapabilityInvocations(),p.maxTokenBudget(),p.maxEstimatedCost(),p.maxExecutionTimeSeconds()); }

    private String normalizeStepId(String v){String x=requireNonBlank(v,"stepId").trim();if(!STEP_ID.matcher(x).matches())throw new IllegalArgumentException("stepId must be a stable plan-local identifier");return x;}
    private List<String> cleanStepIds(List<String> v){if(v==null)return List.of();LinkedHashSet<String> out=new LinkedHashSet<>();for(String s:v)out.add(normalizeStepId(s));return List.copyOf(out);}
    private String normalizeOptionalClassification(String v){if(blank(v))return null;String x=v.trim().toUpperCase(Locale.ROOT).replace(' ','_');if(!CLASSIFICATION_CODE.matcher(x).matches())throw new IllegalArgumentException("classificationCode must be a system-neutral taxonomy code");return x;}
    private String normalizePolicyStatus(String v){String x=blank(v)?"DRAFT":v.trim().toUpperCase(Locale.ROOT);if(!POLICY_STATUSES.contains(x))throw new IllegalArgumentException("Unsupported Execution Plan Policy status: "+x);return x;}
    private String normalizeProposerType(String v){String x=blank(v)?"PLANNER_AGENT":v.trim().toUpperCase(Locale.ROOT);if(!PROPOSER_TYPES.contains(x))throw new IllegalArgumentException("Unsupported proposerType: "+x);return x;}
    private String normalizeUpper(String v){return blank(v)?null:v.trim().toUpperCase(Locale.ROOT).replace(' ','_');}
    private int between(int v,int min,int max,String field){if(v<min||v>max)throw new IllegalArgumentException(field+" must be between "+min+" and "+max);return v;}
    private Long positiveNullable(Long v,String field){if(v!=null&&v<=0)throw new IllegalArgumentException(field+" must be positive");return v;}
    private Double nonNegativeNullable(Double v,String field){if(v!=null&&(v<0||!Double.isFinite(v)))throw new IllegalArgumentException(field+" must be finite and non-negative");return v;}
    private int normalizeLimit(int v){return Math.max(1,Math.min(v<=0?100:v,500));}
    private List<String> cleanStrings(List<String> values){if(values==null)return List.of();LinkedHashSet<String> out=new LinkedHashSet<>();for(String v:values)if(!blank(v))out.add(v.trim());return List.copyOf(out);}
    private List<String> distinct(List<String> values){return List.copyOf(new LinkedHashSet<>(values));}
    private List<String> merge(List<String> a,List<String> b){List<String>x=new ArrayList<>();if(a!=null)x.addAll(a);if(b!=null)x.addAll(b);return distinct(x);}
    private String requireTenant(String v){return requireNonBlank(v,"tenantId");}
    private String requireNonBlank(String v,String f){if(blank(v))throw new IllegalArgumentException(f+" is required");return v.trim();}
    private String firstNonBlank(String a,String b){return !blank(a)?a:b;}
    private String trim(String v){return blank(v)?null:v.trim();}
    private boolean blank(String v){return v==null||v.isBlank();}

    private void bindDatabaseTenantContext(String tenant) {
        IamTenantExecutionContext c= IamTenantContextHolder.current().orElse(null);
        if(c!=null&&!"INSTANCE".equalsIgnoreCase(c.tenantId())&&!tenant.equals(c.tenantId())) throw new IllegalArgumentException("Tenant context mismatch for Execution Plan persistence");
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actorRef());
    }
    private String actorRef(){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);return c==null||blank(c.actorId())?"execution-plan-management":c.actorId();}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalArgumentException("Execution Plan JSON cannot be serialized",ex);}}
    private Map<String,Object> readMap(String v){try{return blank(v)?Map.of():json.readValue(v,OBJECT_MAP);}catch(Exception ex){throw new IllegalStateException("Execution Plan map JSON cannot be read",ex);}}
    private List<String> readStrings(String v){try{return blank(v)?List.of():json.readValue(v,STRING_LIST);}catch(Exception ex){throw new IllegalStateException("Execution Plan list JSON cannot be read",ex);}}
    private List<CapabilityRequirement> readRequirements(String v){try{return blank(v)?List.of():json.readValue(v,REQUIREMENTS);}catch(Exception ex){throw new IllegalStateException("Execution Plan requirements JSON cannot be read",ex);}}
    private List<ExecutionPlanStep> readSteps(String v){try{return blank(v)?List.of():json.readValue(v,STEPS);}catch(Exception ex){throw new IllegalStateException("Execution Plan steps JSON cannot be read",ex);}}
    private CapabilityRequirement readRequirement(String v){try{return json.readValue(v,CapabilityRequirement.class);}catch(Exception ex){throw new IllegalStateException("Execution Plan CapabilityRequirement JSON cannot be read",ex);}}

    private final class PolicyMapper implements RowMapper<ExecutionPlanPolicy>{public ExecutionPlanPolicy mapRow(ResultSet rs,int n)throws SQLException{return new ExecutionPlanPolicy(rs.getString("tenant_id"),rs.getString("policy_id"),rs.getString("display_name"),rs.getInt("max_steps"),rs.getInt("max_plan_depth"),rs.getInt("max_concurrent_branches"),rs.getInt("max_capability_invocations"),(Long)rs.getObject("max_token_budget"),rs.getObject("max_estimated_cost")==null?null:rs.getDouble("max_estimated_cost"),(Long)rs.getObject("max_execution_time_seconds"),rs.getBoolean("require_human_review_on_plan_change"),rs.getString("status"),rs.getInt("version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));}}
    private final class RequestMapper implements RowMapper<ExecutionPlanRequest>{public ExecutionPlanRequest mapRow(ResultSet rs,int n)throws SQLException{return new ExecutionPlanRequest(rs.getString("request_id"),rs.getString("tenant_id"),rs.getString("task_ref"),rs.getString("source_triage_decision_id"),rs.getString("classification_code"),readRequirements(rs.getString("initial_requirements_json")),readStrings(rs.getString("context_refs_json")),rs.getString("status"),rs.getObject("created_at",OffsetDateTime.class));}}
    private final class ProposalMapper implements RowMapper<ExecutionPlanProposal>{public ExecutionPlanProposal mapRow(ResultSet rs,int n)throws SQLException{return new ExecutionPlanProposal(rs.getString("proposal_id"),rs.getString("tenant_id"),rs.getString("request_id"),rs.getString("proposer_type"),rs.getString("proposer_ref"),readSteps(rs.getString("steps_json")),rs.getString("rationale"),rs.getObject("proposed_at",OffsetDateTime.class));}}
    private final class DecisionMapper implements RowMapper<ExecutionPlanDecision>{public ExecutionPlanDecision mapRow(ResultSet rs,int n)throws SQLException{return new ExecutionPlanDecision(rs.getString("decision_id"),rs.getString("tenant_id"),rs.getString("request_id"),rs.getString("proposal_id"),rs.getString("decision_mode"),rs.getString("result"),rs.getString("plan_id"),(Integer)rs.getObject("plan_revision"),rs.getString("policy_id"),(Integer)rs.getObject("policy_version"),(Integer)rs.getObject("max_depth_observed"),(Integer)rs.getObject("max_concurrent_branches_observed"),readStrings(rs.getString("reason_codes_json")),rs.getBoolean("requires_human_review"),rs.getObject("decided_at",OffsetDateTime.class));}}
    private final class PlanHeaderMapper implements RowMapper<PlanHeader>{public PlanHeader mapRow(ResultSet rs,int n)throws SQLException{return new PlanHeader(rs.getString("tenant_id"),rs.getString("plan_id"),rs.getString("request_id"),rs.getString("task_ref"),rs.getString("source_triage_decision_id"),rs.getString("classification_code"),rs.getString("policy_id"),rs.getInt("policy_version"),rs.getString("status"),rs.getInt("current_revision"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));}}

    private record GraphMetrics(int maxDepth,int maxConcurrentBranches){}
    private record Validation(List<ExecutionPlanStep> steps,List<String> errors,List<String> capabilityGaps,int maxDepth,int maxConcurrentBranches){}
    private record PlanHeader(String tenantId,String planId,String requestId,String taskRef,String sourceTriageDecisionId,String classificationCode,String policyId,int policyVersion,String status,int currentRevision,OffsetDateTime createdAt,OffsetDateTime updatedAt){}
    private record TriageSourceEvidence(String result,List<CapabilityRequirement> requirements){}
}
