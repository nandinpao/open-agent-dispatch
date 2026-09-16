package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * Phase 6 UNKNOWN-problem / Semantic Triage authority.
 *
 * <p>Known Service Code mappings bypass semantic reasoning. Unknown work can receive a semantic proposal,
 * but a Triage Agent can only propose classification and Canonical Capability requirements. This service
 * never discovers providers, authorizes providers, ranks providers or resolves Netty/A2A/MCP execution.</p>
 */
@Service
public class SemanticTriageService {
    private static final Pattern CLASSIFICATION_CODE = Pattern.compile("^[A-Z][A-Z0-9_:-]{2,127}$");
    private static final Set<String> POLICY_STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED", "RETIRED");
    private static final Set<String> PROPOSER_TYPES = Set.of("TRIAGE_AGENT", "HUMAN_ANALYST", "SYSTEM_IMPORT");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String,Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<List<CapabilityRequirement>> REQUIREMENTS = new TypeReference<>() {};
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CanonicalCapabilityManagementService capabilities;

    public SemanticTriageService(NamedParameterJdbcTemplate jdbc, ObjectMapper json,
                                 CanonicalCapabilityManagementService capabilities) {
        this.jdbc = jdbc;
        this.json = json;
        this.capabilities = capabilities;
    }

    @Transactional(readOnly = true)
    public List<TriagePolicy> listPolicies(String tenantId, String status, int limit) {
        String tenant = requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", tenant).addValue("limit", normalizeLimit(limit));
        String where = " where tenant_id=:tenant";
        if (!blank(status)) { where += " and status=:status"; p.addValue("status", normalizePolicyStatus(status)); }
        return jdbc.query("select * from semantic_triage_policies" + where + " order by policy_id asc limit :limit", p, new PolicyMapper());
    }

    @Transactional(readOnly = true)
    public Optional<TriagePolicy> findPolicy(String tenantId, String policyId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        try {
            return Optional.ofNullable(jdbc.queryForObject("select * from semantic_triage_policies where tenant_id=:tenant and policy_id=:id",
                    new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(policyId,"policyId")), new PolicyMapper()));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    @Transactional
    public TriagePolicy upsertPolicy(String tenantId, String pathPolicyId, TriagePolicy request, String changeReason) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        if (request==null) throw new IllegalArgumentException("Triage Policy request body is required");
        String id=requireNonBlank(firstNonBlank(pathPolicyId,request.policyId()),"policyId");
        if (!blank(request.policyId()) && !id.equals(request.policyId().trim())) throw new IllegalArgumentException("policyId in request body must match path");
        String status=normalizePolicyStatus(request.status());
        double classification=unit(request.minClassificationConfidence(),"minClassificationConfidence");
        double capability=unit(request.minCapabilityResolutionConfidence(),"minCapabilityResolutionConfidence");
        int max=request.maxCapabilitySuggestions()<=0?10:request.maxCapabilitySuggestions();
        if (max>100) throw new IllegalArgumentException("maxCapabilitySuggestions must be between 1 and 100");
        if ("ACTIVE".equals(status)) {
            Integer active=jdbc.queryForObject("select count(*) from semantic_triage_policies where tenant_id=:tenant and status='ACTIVE' and policy_id<>:id",
                    new MapSqlParameterSource("tenant",tenant).addValue("id",id),Integer.class);
            if (active!=null && active>0) throw new IllegalArgumentException("Only one ACTIVE Semantic Triage Policy is allowed per Tenant");
        }
        TriagePolicy existing=findPolicy(tenant,id).orElse(null);
        int version=existing==null?1:existing.version()+1;
        OffsetDateTime now=OffsetDateTime.now();
        OffsetDateTime created=existing==null?now:existing.createdAt();
        if (existing!=null && blank(changeReason)) throw new IllegalArgumentException("X-Change-Reason is required when changing an existing Triage Policy");
        jdbc.update("""
            insert into semantic_triage_policies(tenant_id,policy_id,display_name,min_classification_confidence,min_capability_resolution_confidence,
              max_capability_suggestions,require_human_review_on_capability_gap,status,version,created_at,updated_at)
            values(:tenant,:id,:name,:classification,:capability,:max,:human,:status,:version,:created,:updated)
            on conflict(tenant_id,policy_id) do update set display_name=excluded.display_name,
              min_classification_confidence=excluded.min_classification_confidence,
              min_capability_resolution_confidence=excluded.min_capability_resolution_confidence,
              max_capability_suggestions=excluded.max_capability_suggestions,
              require_human_review_on_capability_gap=excluded.require_human_review_on_capability_gap,
              status=excluded.status,version=excluded.version,updated_at=excluded.updated_at
            """, new MapSqlParameterSource("tenant",tenant).addValue("id",id)
                .addValue("name",requireNonBlank(request.displayName(),"displayName"))
                .addValue("classification",classification).addValue("capability",capability).addValue("max",max)
                .addValue("human",request.requireHumanReviewOnCapabilityGap()).addValue("status",status).addValue("version",version)
                .addValue("created",created).addValue("updated",now));
        TriagePolicy saved=findPolicy(tenant,id).orElseThrow();
        appendPolicyVersion(saved, existing==null?"INITIAL_CREATE":requireNonBlank(changeReason,"changeReason"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TriagePolicyVersion> policyVersions(String tenantId, String policyId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        return jdbc.query("select * from semantic_triage_policy_versions where tenant_id=:tenant and policy_id=:id order by version desc",
                new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(policyId,"policyId")),
                (rs,n)->new TriagePolicyVersion(rs.getString("tenant_id"),rs.getString("policy_id"),rs.getInt("version"),
                        readMap(rs.getString("snapshot_json")),rs.getString("change_reason"),rs.getString("actor_ref"),
                        rs.getObject("created_at",OffsetDateTime.class)));
    }

    /** Exact Service Code mapping is the Phase 6 Fast Path and never calls a Triage Agent. */
    @Transactional
    public TriageDecision resolvePreview(String tenantId, TriagePreviewRequest preview) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        if (preview==null) throw new IllegalArgumentException("Triage preview request body is required");
        if (blank(preview.serviceCode()) && blank(preview.problemStatement())) throw new IllegalArgumentException("serviceCode or problemStatement is required");
        String requestId="triage-request-"+UUID.randomUUID();
        OffsetDateTime now=OffsetDateTime.now();
        List<CapabilityRequirement> known=blank(preview.serviceCode())?List.of():capabilities.requirementsForServiceCode(tenant,preview.serviceCode());
        String status=known.isEmpty()?"TRIAGE_REQUIRED":"KNOWN_FAST_PATH";
        jdbc.update("""
            insert into semantic_triage_requests(tenant_id,request_id,task_ref,service_code,problem_statement,context_refs_json,input_context_json,
              data_classification,status,created_at) values(:tenant,:id,:task,:service,:problem,cast(:refs as jsonb),cast(:ctx as jsonb),:data,:status,:created)
            """, new MapSqlParameterSource("tenant",tenant).addValue("id",requestId).addValue("task",trim(preview.taskRef()))
                .addValue("service",normalizeOptionalServiceCode(preview.serviceCode())).addValue("problem",trim(preview.problemStatement()))
                .addValue("refs",write(preview.contextRefs()==null?List.of():preview.contextRefs()))
                .addValue("ctx",write(preview.inputContext()==null?Map.of():preview.inputContext()))
                .addValue("data",normalizeUpper(preview.dataClassification())).addValue("status",status).addValue("created",now));
        if (!known.isEmpty()) {
            return persistDecision(tenant,requestId,"PREVIEW","KNOWN_FAST_PATH",normalizeOptionalServiceCode(preview.serviceCode()),null,1.0d,
                    null,null,known,List.of("EXACT_SERVICE_CODE_MAPPING","TRIAGE_AGENT_NOT_INVOKED","WHAT_RESOLVED_WITHOUT_PROVIDER_SELECTION"),false);
        }
        return persistDecision(tenant,requestId,"PREVIEW","TRIAGE_REQUIRED",normalizeOptionalServiceCode(preview.serviceCode()),null,0.0d,
                null,null,List.of(),List.of("NO_EXACT_SERVICE_CODE_CAPABILITY_MAPPING","SEMANTIC_TRIAGE_REQUIRED","NO_PROVIDER_ROUTING_OR_EXECUTION"),false);
    }

    @Transactional
    public TriageDecision submitProposal(String tenantId, String requestId, TriageProposal proposal) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        TriageRequest request=requireRequest(tenant,requestId);
        if (!"TRIAGE_REQUIRED".equals(request.status())) throw new IllegalArgumentException("Semantic proposal is allowed only for TRIAGE_REQUIRED requests");
        if (proposal==null) throw new IllegalArgumentException("Triage Proposal request body is required");
        if (!blank(proposal.requestId()) && !requestId.equals(proposal.requestId().trim())) throw new IllegalArgumentException("requestId in proposal body must match path");
        if (!blank(proposal.tenantId()) && !tenant.equals(proposal.tenantId().trim())) throw new IllegalArgumentException("tenantId in proposal body must match active Tenant");
        if (proposal.classification()==null) throw new IllegalArgumentException("ProblemClassification is required");
        String classificationCode=normalizeClassificationCode(proposal.classification().classificationCode());
        String proposerType=normalizeProposerType(proposal.proposerType());
        TriagePolicy policy=activePolicy(tenant).orElse(null);
        String proposalId="triage-proposal-"+UUID.randomUUID();
        OffsetDateTime now=OffsetDateTime.now();
        ProblemClassification classification=new ProblemClassification(classificationCode,trim(proposal.classification().displayName()),
                trim(proposal.classification().semanticTaxonomy()),proposal.classification().confidence(),trim(proposal.classification().rationale()));
        List<TriageCapabilitySuggestion> suggestions=dedupeSuggestions(proposal.capabilitySuggestions());
        persistProposal(tenant,proposalId,requestId,proposerType,proposal.proposerRef(),classification,
                proposal.capabilityResolutionConfidence(),suggestions,proposal.investigationSuggestions(),proposal.explanatoryTaxonomies(),proposal.rationale(),now);
        if (policy==null) {
            return persistDecision(tenant,requestId,"PREVIEW","TRIAGE_POLICY_NOT_CONFIGURED",request.serviceCode(),classification,
                    proposal.capabilityResolutionConfidence(),null,null,List.of(),
                    List.of("TRIAGE_POLICY_NOT_CONFIGURED","FAIL_CLOSED_NO_SEMANTIC_ACCEPTANCE","TRIAGE_AGENT_PROPOSAL_IS_NOT_AUTHORITY"),true);
        }
        if (suggestions.size()>policy.maxCapabilitySuggestions()) {
            return persistDecision(tenant,requestId,"PREVIEW","HUMAN_REVIEW_REQUIRED",request.serviceCode(),classification,
                    proposal.capabilityResolutionConfidence(),policy.policyId(),policy.version(),List.of(),
                    List.of("CAPABILITY_SUGGESTION_LIMIT_EXCEEDED","TRIAGE_AGENT_PROPOSAL_REQUIRES_REVIEW"),true);
        }
        List<String> reasons=new ArrayList<>();
        boolean lowClassification=classification.confidence()<policy.minClassificationConfidence();
        boolean lowCapabilities=proposal.capabilityResolutionConfidence()<policy.minCapabilityResolutionConfidence();
        if (lowClassification) reasons.add("CLASSIFICATION_CONFIDENCE_BELOW_POLICY");
        if (lowCapabilities) reasons.add("CAPABILITY_RESOLUTION_CONFIDENCE_BELOW_POLICY");
        List<CapabilityRequirement> accepted=new ArrayList<>();
        List<String> gaps=new ArrayList<>();
        boolean lowSuggestionConfidence=false;
        for (TriageCapabilitySuggestion suggestion:suggestions) {
            if (suggestion.confidence()<policy.minCapabilityResolutionConfidence()) { reasons.add("CAPABILITY_SUGGESTION_CONFIDENCE_BELOW_POLICY:"+suggestion.capabilityCode()); lowSuggestionConfidence=true; }
            var definition=capabilities.find(tenant,suggestion.capabilityCode()).orElse(null);
            if (definition==null || !"ACTIVE".equals(definition.status())) { gaps.add(suggestion.capabilityCode()); continue; }
            String op=normalizeUpper(suggestion.operation());
            if (blank(op) || !definition.operations().contains(op)) { gaps.add(suggestion.capabilityCode()+":"+firstNonBlank(op,"<missing-operation>")); continue; }
            accepted.add(new CapabilityRequirement(definition.capabilityCode(),op,request.inputContext(),Map.of(),request.dataClassification(),null,null,null));
        }
        if (!gaps.isEmpty()) {
            reasons.add("CAPABILITY_GAP"); reasons.addAll(gaps.stream().map(x->"UNRESOLVED_CAPABILITY:"+x).toList());
            return persistDecision(tenant,requestId,"PREVIEW","CAPABILITY_GAP",request.serviceCode(),classification,
                    proposal.capabilityResolutionConfidence(),policy.policyId(),policy.version(),List.of(),reasons,
                    policy.requireHumanReviewOnCapabilityGap());
        }
        if (suggestions.isEmpty()) {
            reasons.add("NO_CAPABILITY_SUGGESTION");
            return persistDecision(tenant,requestId,"PREVIEW","HUMAN_REVIEW_REQUIRED",request.serviceCode(),classification,
                    proposal.capabilityResolutionConfidence(),policy.policyId(),policy.version(),List.of(),reasons,true);
        }
        if (lowClassification || lowCapabilities || lowSuggestionConfidence) {
            reasons.add("CONFIDENCE_POLICY_REQUIRES_HUMAN_REVIEW");
            return persistDecision(tenant,requestId,"PREVIEW","HUMAN_REVIEW_REQUIRED",request.serviceCode(),classification,
                    proposal.capabilityResolutionConfidence(),policy.policyId(),policy.version(),List.of(),reasons,true);
        }
        reasons.add("TRIAGE_PROPOSAL_VALIDATED_BY_OPENDISPATCH");
        reasons.add("CANONICAL_CAPABILITY_REQUIREMENTS_ACCEPTED");
        reasons.add("NO_PROVIDER_AUTHORIZATION_RANKING_OR_EXECUTION_OCCURRED");
        jdbc.update("update semantic_triage_requests set status='REQUIREMENTS_PROPOSED' where tenant_id=:tenant and request_id=:id",
                new MapSqlParameterSource("tenant",tenant).addValue("id",requestId));
        return persistDecision(tenant,requestId,"PREVIEW","CAPABILITY_REQUIREMENTS_PROPOSED",request.serviceCode(),classification,
                proposal.capabilityResolutionConfidence(),policy.policyId(),policy.version(),accepted,reasons,false);
    }

    @Transactional(readOnly = true)
    public Optional<TriageDecision> findLatestAcceptedDecisionForTask(String tenantId, String taskRef) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "select d.* from semantic_triage_decisions d join semantic_triage_requests r on r.tenant_id=d.tenant_id and r.request_id=d.request_id where d.tenant_id=:tenant and r.task_ref=:task and d.result='CAPABILITY_REQUIREMENTS_PROPOSED' and d.requires_human_review=false order by d.decided_at desc limit 1",
                    new MapSqlParameterSource("tenant",tenant).addValue("task",requireNonBlank(taskRef,"taskRef")), new DecisionMapper()));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    @Transactional(readOnly = true)
    public Optional<TriageRequest> findRequest(String tenantId, String requestId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        try { return Optional.of(requireRequest(tenant, requireNonBlank(requestId,"requestId"))); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }

    @Transactional(readOnly = true)
    public List<TriageRequest> listRequests(String tenantId, String status, int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("limit",normalizeLimit(limit));
        String where=" where tenant_id=:tenant";
        if (!blank(status)) { where+=" and status=:status"; p.addValue("status",status.trim().toUpperCase(Locale.ROOT)); }
        return jdbc.query("select * from semantic_triage_requests"+where+" order by created_at desc limit :limit",p,new RequestMapper());
    }

    @Transactional(readOnly = true)
    public List<TriageProposal> listProposals(String tenantId, String requestId, int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        return jdbc.query("select * from semantic_triage_proposals where tenant_id=:tenant and (:requestId is null or request_id=:requestId) order by proposed_at desc limit :limit",
                new MapSqlParameterSource("tenant",tenant).addValue("requestId",blank(requestId)?null:requestId.trim()).addValue("limit",normalizeLimit(limit)),
                new ProposalMapper());
    }

    @Transactional(readOnly = true)
    public List<TriageDecision> listDecisions(String tenantId, String requestId, int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        return jdbc.query("select * from semantic_triage_decisions where tenant_id=:tenant and (:requestId is null or request_id=:requestId) order by decided_at desc limit :limit",
                new MapSqlParameterSource("tenant",tenant).addValue("requestId",blank(requestId)?null:requestId.trim()).addValue("limit",normalizeLimit(limit)),
                new DecisionMapper());
    }

    private void appendPolicyVersion(TriagePolicy p,String reason) {
        jdbc.update("insert into semantic_triage_policy_versions(tenant_id,policy_id,version,snapshot_json,change_reason,actor_ref,created_at) values(:tenant,:id,:version,cast(:snapshot as jsonb),:reason,:actor,:created)",
                new MapSqlParameterSource("tenant",p.tenantId()).addValue("id",p.policyId()).addValue("version",p.version())
                    .addValue("snapshot",write(p)).addValue("reason",reason).addValue("actor",actorRef()).addValue("created",OffsetDateTime.now()));
    }

    private void persistProposal(String tenant,String proposalId,String requestId,String proposerType,String proposerRef,
                                 ProblemClassification classification,double capabilityConfidence,List<TriageCapabilitySuggestion> suggestions,
                                 List<String> investigation,List<String> taxonomy,String rationale,OffsetDateTime at) {
        jdbc.update("""
            insert into semantic_triage_proposals(tenant_id,proposal_id,request_id,proposer_type,proposer_ref,classification_json,
              classification_confidence,capability_resolution_confidence,capability_suggestions_json,investigation_suggestions_json,
              explanatory_taxonomies_json,rationale,proposed_at)
            values(:tenant,:proposal,:request,:type,:ref,cast(:classification as jsonb),:classificationConfidence,:capabilityConfidence,
              cast(:suggestions as jsonb),cast(:investigation as jsonb),cast(:taxonomy as jsonb),:rationale,:at)
            """, new MapSqlParameterSource("tenant",tenant).addValue("proposal",proposalId).addValue("request",requestId)
                .addValue("type",proposerType).addValue("ref",trim(proposerRef)).addValue("classification",write(classification))
                .addValue("classificationConfidence",classification.confidence()).addValue("capabilityConfidence",unit(capabilityConfidence,"capabilityResolutionConfidence"))
                .addValue("suggestions",write(suggestions)).addValue("investigation",write(investigation==null?List.of():investigation))
                .addValue("taxonomy",write(taxonomy==null?List.of():taxonomy)).addValue("rationale",trim(rationale)).addValue("at",at));
    }

    private TriageDecision persistDecision(String tenant,String requestId,String mode,String result,String serviceCode,
                                           ProblemClassification classification,double capabilityConfidence,String policyId,Integer policyVersion,
                                           List<CapabilityRequirement> requirements,List<String> reasons,boolean human) {
        String id="triage-decision-"+UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now();
        jdbc.update("""
            insert into semantic_triage_decisions(tenant_id,decision_id,request_id,decision_mode,result,service_code,classification_json,
              classification_confidence,capability_resolution_confidence,triage_policy_id,triage_policy_version,accepted_requirements_json,
              reason_codes_json,requires_human_review,decided_at)
            values(:tenant,:id,:request,:mode,:result,:service,cast(:classification as jsonb),:classificationConfidence,:capabilityConfidence,
              :policy,:policyVersion,cast(:requirements as jsonb),cast(:reasons as jsonb),:human,:at)
            """, new MapSqlParameterSource("tenant",tenant).addValue("id",id).addValue("request",requestId).addValue("mode",mode)
                .addValue("result",result).addValue("service",serviceCode).addValue("classification",classification==null?null:write(classification))
                .addValue("classificationConfidence",classification==null?0.0d:classification.confidence()).addValue("capabilityConfidence",capabilityConfidence)
                .addValue("policy",policyId).addValue("policyVersion",policyVersion).addValue("requirements",write(requirements==null?List.of():requirements))
                .addValue("reasons",write(reasons==null?List.of():reasons)).addValue("human",human).addValue("at",now));
        return new TriageDecision(id,tenant,requestId,mode,result,serviceCode,classification,classification==null?0.0d:classification.confidence(),capabilityConfidence,policyId,policyVersion,
                requirements,reasons,human,now);
    }

    private Optional<TriagePolicy> activePolicy(String tenant) {
        try { return Optional.ofNullable(jdbc.queryForObject("select * from semantic_triage_policies where tenant_id=:tenant and status='ACTIVE'",
                new MapSqlParameterSource("tenant",tenant),new PolicyMapper())); }
        catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    private TriageRequest requireRequest(String tenant,String id) {
        try { return jdbc.queryForObject("select * from semantic_triage_requests where tenant_id=:tenant and request_id=:id",
                new MapSqlParameterSource("tenant",tenant).addValue("id",requireNonBlank(id,"requestId")),new RequestMapper()); }
        catch (EmptyResultDataAccessException ex) { throw new IllegalArgumentException("Triage Request not found: "+id); }
    }

    private List<TriageCapabilitySuggestion> dedupeSuggestions(List<TriageCapabilitySuggestion> values) {
        if (values==null) return List.of();
        LinkedHashSet<String> seen=new LinkedHashSet<>(); List<TriageCapabilitySuggestion> out=new ArrayList<>();
        for (TriageCapabilitySuggestion s:values) {
            if (s==null) continue;
            String code=requireNonBlank(s.capabilityCode(),"capabilityCode").trim().toLowerCase(Locale.ROOT);
            String op=normalizeUpper(s.operation()); String key=code+"|"+op;
            if (seen.add(key)) out.add(new TriageCapabilitySuggestion(code,op,unit(s.confidence(),"capability suggestion confidence"),trim(s.rationale())));
        }
        return List.copyOf(out);
    }

    private String normalizeClassificationCode(String value) {
        String v=requireNonBlank(value,"classificationCode").trim().toUpperCase(Locale.ROOT).replace(' ','_');
        if (!CLASSIFICATION_CODE.matcher(v).matches()) throw new IllegalArgumentException("classificationCode must be a system-neutral taxonomy code");
        return v;
    }
    private String normalizePolicyStatus(String v) { String x=blank(v)?"DRAFT":v.trim().toUpperCase(Locale.ROOT); if(!POLICY_STATUSES.contains(x)) throw new IllegalArgumentException("Unsupported Triage Policy status: "+x); return x; }
    private String normalizeProposerType(String v) { String x=blank(v)?"TRIAGE_AGENT":v.trim().toUpperCase(Locale.ROOT); if(!PROPOSER_TYPES.contains(x)) throw new IllegalArgumentException("Unsupported proposerType: "+x); return x; }
    private String normalizeOptionalServiceCode(String v) { return blank(v)?null:v.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]","_"); }
    private String normalizeUpper(String v) { return blank(v)?null:v.trim().toUpperCase(Locale.ROOT).replace(' ','_'); }
    private double unit(double v,String field) { if(v<0.0d||v>1.0d) throw new IllegalArgumentException(field+" must be between 0 and 1"); return v; }
    private int normalizeLimit(int v) { return Math.max(1,Math.min(v<=0?100:v,500)); }
    private String requireTenant(String v) { return requireNonBlank(v,"tenantId"); }
    private String requireNonBlank(String v,String field) { if(blank(v)) throw new IllegalArgumentException(field+" is required"); return v.trim(); }
    private String firstNonBlank(String a,String b) { return !blank(a)?a:b; }
    private String trim(String v) { return blank(v)?null:v.trim(); }
    private boolean blank(String v) { return v==null||v.isBlank(); }

    private void bindDatabaseTenantContext(String tenant) {
        IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);
        if(c!=null && !"INSTANCE".equalsIgnoreCase(c.tenantId()) && !tenant.equals(c.tenantId())) throw new IllegalArgumentException("Tenant context mismatch for Semantic Triage persistence");
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actorRef());
    }
    private String actorRef() { IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null); return c==null||blank(c.actorId())?"semantic-triage-management":c.actorId(); }
    private String write(Object v) { try { return json.writeValueAsString(v); } catch(Exception ex) { throw new IllegalArgumentException("Semantic Triage JSON cannot be serialized",ex); } }
    private Map<String,Object> readMap(String v) { try { return blank(v)?Map.of():json.readValue(v,OBJECT_MAP); } catch(Exception ex) { throw new IllegalStateException("Semantic Triage map JSON cannot be read",ex); } }
    private List<String> readStrings(String v) { try { return blank(v)?List.of():json.readValue(v,STRING_LIST); } catch(Exception ex) { throw new IllegalStateException("Semantic Triage list JSON cannot be read",ex); } }
    private List<CapabilityRequirement> readRequirements(String v) { try { return blank(v)?List.of():json.readValue(v,REQUIREMENTS); } catch(Exception ex) { throw new IllegalStateException("Capability Requirement JSON cannot be read",ex); } }
    private ProblemClassification readClassification(String v) { try { return blank(v)?null:json.readValue(v,ProblemClassification.class); } catch(Exception ex) { throw new IllegalStateException("Problem Classification JSON cannot be read",ex); } }
    private List<TriageCapabilitySuggestion> readSuggestions(String v) { try { return blank(v)?List.of():json.readValue(v,new TypeReference<List<TriageCapabilitySuggestion>>(){}); } catch(Exception ex) { throw new IllegalStateException("Triage Capability Suggestion JSON cannot be read",ex); } }

    private final class PolicyMapper implements RowMapper<TriagePolicy> { public TriagePolicy mapRow(ResultSet rs,int n)throws SQLException { return new TriagePolicy(rs.getString("tenant_id"),rs.getString("policy_id"),rs.getString("display_name"),rs.getDouble("min_classification_confidence"),rs.getDouble("min_capability_resolution_confidence"),rs.getInt("max_capability_suggestions"),rs.getBoolean("require_human_review_on_capability_gap"),rs.getString("status"),rs.getInt("version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class)); } }
    private final class RequestMapper implements RowMapper<TriageRequest> { public TriageRequest mapRow(ResultSet rs,int n)throws SQLException { return new TriageRequest(rs.getString("request_id"),rs.getString("tenant_id"),rs.getString("task_ref"),rs.getString("service_code"),rs.getString("problem_statement"),readStrings(rs.getString("context_refs_json")),readMap(rs.getString("input_context_json")),rs.getString("data_classification"),rs.getString("status"),rs.getObject("created_at",OffsetDateTime.class)); } }
    private final class ProposalMapper implements RowMapper<TriageProposal> { public TriageProposal mapRow(ResultSet rs,int n)throws SQLException { return new TriageProposal(rs.getString("proposal_id"),rs.getString("tenant_id"),rs.getString("request_id"),rs.getString("proposer_type"),rs.getString("proposer_ref"),readClassification(rs.getString("classification_json")),rs.getDouble("capability_resolution_confidence"),readSuggestions(rs.getString("capability_suggestions_json")),readStrings(rs.getString("investigation_suggestions_json")),readStrings(rs.getString("explanatory_taxonomies_json")),rs.getString("rationale"),rs.getObject("proposed_at",OffsetDateTime.class)); } }
    private final class DecisionMapper implements RowMapper<TriageDecision> { public TriageDecision mapRow(ResultSet rs,int n)throws SQLException { return new TriageDecision(rs.getString("decision_id"),rs.getString("tenant_id"),rs.getString("request_id"),rs.getString("decision_mode"),rs.getString("result"),rs.getString("service_code"),readClassification(rs.getString("classification_json")),rs.getDouble("classification_confidence"),rs.getDouble("capability_resolution_confidence"),rs.getString("triage_policy_id"),(Integer)rs.getObject("triage_policy_version"),readRequirements(rs.getString("accepted_requirements_json")),readStrings(rs.getString("reason_codes_json")),rs.getBoolean("requires_human_review"),rs.getObject("decided_at",OffsetDateTime.class)); } }
}
