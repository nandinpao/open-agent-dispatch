package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 5 HOW authority.
 *
 * <p>This service may resolve an execution adapter only from an immutable Phase 4 SELECTED
 * routing decision. It never discovers, authorizes or ranks providers. Endpoint and credential
 * values are opaque registry references; raw URLs, tokens and secrets are rejected.</p>
 */
@Service
public class ExecutionAdapterService {
    private static final Set<String> PROVIDER_TYPES = Set.of("MANAGED_AGENT", "REMOTE_A2A_AGENT", "MCP_TOOL", "INTERNAL_SERVICE");
    private static final Set<String> ADAPTER_TYPES = Set.of("MANAGED_AGENT_NETTY", "REMOTE_A2A", "MCP_TOOL", "INTERNAL_SERVICE");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "RETIRED");
    private static final Map<String, Set<String>> ADAPTERS_BY_PROVIDER = Map.of(
            "MANAGED_AGENT", Set.of("MANAGED_AGENT_NETTY"),
            "REMOTE_A2A_AGENT", Set.of("REMOTE_A2A"),
            "MCP_TOOL", Set.of("MCP_TOOL"),
            "INTERNAL_SERVICE", Set.of("INTERNAL_SERVICE"));
    private static final Map<String, Set<String>> PROTOCOLS_BY_ADAPTER = Map.of(
            "MANAGED_AGENT_NETTY", Set.of("NETTY_TCP", "NETTY_WEBSOCKET"),
            "REMOTE_A2A", Set.of("A2A_JSON_RPC", "A2A_GRPC", "A2A_HTTP_JSON"),
            "MCP_TOOL", Set.of("MCP_STREAMABLE_HTTP", "MCP_SSE", "MCP_STDIO"),
            "INTERNAL_SERVICE", Set.of("INTERNAL_HTTP_JSON", "INTERNAL_GRPC"));
    private static final TypeReference<Map<String,Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ProviderRoutingCandidateScore>> CANDIDATE_LIST = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ExecutionAdapterService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ExecutionAdapterRegistration> listAdapters(String tenantId, String providerId, String status, String search, String afterAdapterId, int limit) {
        String tenant = requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        MapSqlParameterSource p = new MapSqlParameterSource("tenantId", tenant).addValue("limit", normalizeLimit(limit));
        StringBuilder where = new StringBuilder(" where tenant_id=:tenantId");
        if (!blank(providerId)) { where.append(" and provider_id=:providerId"); p.addValue("providerId", providerId.trim()); }
        if (!blank(status)) { where.append(" and status=:status"); p.addValue("status", normalizeStatus(status)); }
        else where.append(" and status<>'RETIRED'");
        if (!blank(search)) { where.append(" and (lower(adapter_id) like :search or lower(provider_id) like :search or lower(adapter_type) like :search or lower(protocol) like :search)"); p.addValue("search", "%"+search.trim().toLowerCase(Locale.ROOT)+"%"); }
        if (!blank(afterAdapterId)) { where.append(" and adapter_id>:afterAdapterId"); p.addValue("afterAdapterId", afterAdapterId.trim()); }
        return List.copyOf(jdbc.query("""
                select tenant_id,adapter_id,provider_id,provider_type,adapter_type,protocol,protocol_version,endpoint_ref,credential_ref,runtime_ref,
                       status,selection_priority,configuration_json,version,created_at,updated_at
                from execution_adapter_registrations
                """ + where + " order by adapter_id asc limit :limit", p, new AdapterRowMapper()));
    }

    @Transactional(readOnly = true)
    public Optional<ExecutionAdapterRegistration> findAdapter(String tenantId, String adapterId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        try { return Optional.ofNullable(jdbc.queryForObject("""
                select tenant_id,adapter_id,provider_id,provider_type,adapter_type,protocol,protocol_version,endpoint_ref,credential_ref,runtime_ref,
                       status,selection_priority,configuration_json,version,created_at,updated_at
                from execution_adapter_registrations where tenant_id=:tenantId and adapter_id=:adapterId
                """, new MapSqlParameterSource("tenantId",tenant).addValue("adapterId",requireNonBlank(adapterId,"adapterId")), new AdapterRowMapper())); }
        catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    @Transactional
    public ExecutionAdapterRegistration upsertAdapter(String tenantId, String pathAdapterId, ExecutionAdapterRegistration request, String reason) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        if (request==null) throw new IllegalArgumentException("Execution Adapter request body is required");
        String adapterId=requireNonBlank(firstNonBlank(pathAdapterId,request.adapterId()),"adapterId");
        if (!blank(request.adapterId()) && !adapterId.equals(request.adapterId().trim())) throw new IllegalArgumentException("adapterId in body must match path");
        ProviderIdentity provider=loadProvider(tenant,requireNonBlank(request.providerId(),"providerId"));
        String providerType=normalizeProviderType(firstNonBlank(request.providerType(),provider.providerType()));
        if (!provider.providerType().equals(providerType)) throw new IllegalArgumentException("providerType must match Capability Provider registry");
        String adapterType=normalizeAdapterType(request.adapterType());
        if (!ADAPTERS_BY_PROVIDER.get(providerType).contains(adapterType)) throw new IllegalArgumentException("adapterType is not valid for providerType " + providerType);
        String protocol=normalizeProtocol(adapterType, request.protocol());
        String status=normalizeStatus(request.status());
        int priority=request.selectionPriority()==null?100:request.selectionPriority();
        if (priority<0 || priority>100000) throw new IllegalArgumentException("selectionPriority must be between 0 and 100000");
        String endpointRef=trimToNull(request.endpointRef()); String credentialRef=trimToNull(request.credentialRef()); String runtimeRef=trimToNull(request.runtimeRef());
        validateOpaqueReference(endpointRef,"endpointRef"); validateOpaqueReference(credentialRef,"credentialRef"); validateOpaqueReference(runtimeRef,"runtimeRef");
        if ("ACTIVE".equals(status)) validateActiveAdapter(provider,adapterType,protocol,endpointRef,credentialRef,runtimeRef);
        ExecutionAdapterRegistration existing=findAdapter(tenant,adapterId).orElse(null);
        if (existing!=null) {
            if (!existing.providerId().equals(provider.providerId()) || !existing.providerType().equals(providerType) || !existing.adapterType().equals(adapterType))
                throw new IllegalArgumentException("Execution Adapter identity (providerId/providerType/adapterType) is immutable; create a new adapterId");
        }
        String changeReason=existing==null?firstNonBlank(reason,"Initial Execution Adapter registration"):requireNonBlank(reason,"X-Change-Reason");
        int version=existing==null?1:existing.version()+1; OffsetDateTime now=OffsetDateTime.now();
        jdbc.update("""
                insert into execution_adapter_registrations(tenant_id,adapter_id,provider_id,provider_type,adapter_type,protocol,protocol_version,endpoint_ref,credential_ref,runtime_ref,status,selection_priority,configuration_json,version,created_at,updated_at)
                values(:tenantId,:adapterId,:providerId,:providerType,:adapterType,:protocol,:protocolVersion,:endpointRef,:credentialRef,:runtimeRef,:status,:priority,cast(:config as jsonb),:version,:createdAt,:updatedAt)
                on conflict(tenant_id,adapter_id) do update set protocol=excluded.protocol,protocol_version=excluded.protocol_version,endpoint_ref=excluded.endpoint_ref,
                  credential_ref=excluded.credential_ref,runtime_ref=excluded.runtime_ref,status=excluded.status,selection_priority=excluded.selection_priority,
                  configuration_json=excluded.configuration_json,version=excluded.version,updated_at=excluded.updated_at
                """, new MapSqlParameterSource("tenantId",tenant).addValue("adapterId",adapterId).addValue("providerId",provider.providerId()).addValue("providerType",providerType)
                .addValue("adapterType",adapterType).addValue("protocol",protocol).addValue("protocolVersion",trimToNull(request.protocolVersion()))
                .addValue("endpointRef",endpointRef).addValue("credentialRef",credentialRef).addValue("runtimeRef",runtimeRef).addValue("status",status)
                .addValue("priority",priority).addValue("config",writeJson(request.configuration())).addValue("version",version)
                .addValue("createdAt",existing==null?now:existing.createdAt()).addValue("updatedAt",now));
        appendVersion(tenant,adapterId,version,changeReason,now);
        return findAdapter(tenant,adapterId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ExecutionAdapterVersion> adapterVersions(String tenantId, String adapterId) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        return List.copyOf(jdbc.query("""
                select tenant_id,adapter_id,version,snapshot_json,change_reason,actor_ref,created_at
                from execution_adapter_versions where tenant_id=:tenantId and adapter_id=:adapterId order by version desc
                """, new MapSqlParameterSource("tenantId",tenant).addValue("adapterId",requireNonBlank(adapterId,"adapterId")), (rs,n)->new ExecutionAdapterVersion(
                rs.getString("tenant_id"),rs.getString("adapter_id"),rs.getInt("version"),readMap(rs.getString("snapshot_json")),rs.getString("change_reason"),rs.getString("actor_ref"),rs.getObject("created_at",OffsetDateTime.class))));
    }

    /** Admin-only HOW resolution. No network call or Task dispatch occurs. */
    @Transactional
    public ExecutionAdapterResolution resolvePreview(String tenantId, ExecutionAdapterResolutionRequest request) {
        return resolve(tenantId, request, "PREVIEW");
    }

    /** Phase 12 server-side HOW runtime resolution. Only RUNTIME Phase 4 evidence is accepted. */
    @Transactional
    public ExecutionAdapterResolution resolveRuntime(String tenantId, ExecutionAdapterResolutionRequest request) {
        return resolve(tenantId, request, "RUNTIME");
    }

    private ExecutionAdapterResolution resolve(String tenantId, ExecutionAdapterResolutionRequest request, String resolutionMode) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        if (request==null) throw new IllegalArgumentException("Execution Adapter resolution request body is required");
        RoutingEvidence routing=loadRoutingEvidence(tenant,requireNonBlank(request.routingDecisionId(),"routingDecisionId"));
        List<String> staleReasons=validateRoutingEvidenceCurrent(tenant,routing,resolutionMode);
        if (!staleReasons.isEmpty()) return persistResolution(tenant,routing,null,resolutionMode,"ROUTING_DECISION_STALE",staleReasons);
        List<ExecutionAdapterRegistration> active=listActiveAdaptersForProvider(tenant,routing.providerId());
        active=active.stream().filter(a->ADAPTERS_BY_PROVIDER.getOrDefault(routing.providerType(),Set.of()).contains(a.adapterType())).toList();
        if (active.isEmpty()) return persistResolution(tenant,routing,null,resolutionMode,"NO_ACTIVE_ADAPTER",List.of("NO_ACTIVE_EXECUTION_ADAPTER"));
        int best=active.stream().map(ExecutionAdapterRegistration::selectionPriority).min(Comparator.naturalOrder()).orElseThrow();
        List<ExecutionAdapterRegistration> winners=active.stream().filter(a->a.selectionPriority()==best).toList();
        if (winners.size()!=1) return persistResolution(tenant,routing,null,resolutionMode,"ADAPTER_AMBIGUOUS",List.of("EXECUTION_ADAPTER_PRIORITY_TIE","FAIL_CLOSED_NO_LEXICAL_ADAPTER_TIE_BREAK"));
        return persistResolution(tenant,routing,winners.get(0),resolutionMode,"SELECTED",List.of("HOW_SELECTED_FROM_PHASE4_ROUTING_DECISION","NO_PROVIDER_REAUTHORIZATION_OR_RERANKING"));
    }

    @Transactional(readOnly = true)
    public List<ExecutionAdapterResolution> listResolutions(String tenantId, String routingDecisionId, int limit) {
        String tenant=requireTenant(tenantId); bindDatabaseTenantContext(tenant);
        MapSqlParameterSource p=new MapSqlParameterSource("tenantId",tenant).addValue("limit",normalizeLimit(limit));
        String where=""; if (!blank(routingDecisionId)) { where=" and routing_decision_id=:routingDecisionId"; p.addValue("routingDecisionId",routingDecisionId.trim()); }
        return List.copyOf(jdbc.query("""
                select tenant_id,resolution_id,resolution_mode,result,routing_decision_id,capability_code,operation,binding_id,provider_id,provider_type,
                       adapter_id,adapter_version,adapter_type,protocol,protocol_version,endpoint_ref,credential_ref,runtime_ref,reason_codes_json,resolved_at
                from execution_adapter_resolutions where tenant_id=:tenantId
                """+where+" order by resolved_at desc,resolution_id desc limit :limit",p,new ResolutionRowMapper()));
    }

    private List<String> validateRoutingEvidenceCurrent(String tenant, RoutingEvidence routing, String expectedMode) {
        List<String> failures=new ArrayList<>();
        if (!expectedMode.equals(routing.decisionMode())) failures.add("PHASE4_DECISION_MODE_MISMATCH");
        if (!"SELECTED".equals(routing.result())) failures.add("PHASE4_ROUTING_NOT_SELECTED");
        if (blank(routing.bindingId())||blank(routing.providerId())) failures.add("PHASE4_SELECTION_MISSING");
        if (!failures.isEmpty()) return failures;
        CurrentBinding binding=loadCurrentBinding(tenant,routing.bindingId());
        if (!routing.providerId().equals(binding.providerId())) failures.add("PHASE4_PROVIDER_BINDING_MISMATCH");
        if (!routing.capabilityCode().equals(binding.capabilityCode())) failures.add("PHASE4_CAPABILITY_BINDING_MISMATCH");
        if (!"REGISTERED".equals(binding.providerStatus())) failures.add("PROVIDER_NOT_REGISTERED");
        if (!"APPROVED".equals(binding.trustStatus())) failures.add("BINDING_NOT_APPROVED");
        if (!binding.supportedOperations().isEmpty() && !binding.supportedOperations().contains(routing.operation())) failures.add("BINDING_OPERATION_NO_LONGER_SUPPORTED");
        ProfileState profile=loadProfileState(tenant,routing.routingProfileId());
        if (!"ACTIVE".equals(profile.status()) || profile.version()!=routing.routingProfileVersion()) failures.add("ROUTING_PROFILE_VERSION_STALE");
        ProviderRoutingCandidateScore selected=routing.candidates().stream().filter(c->routing.bindingId().equals(c.bindingId())&&routing.providerId().equals(c.providerId())).findFirst().orElse(null);
        if (selected==null) failures.add("SELECTED_CANDIDATE_EVIDENCE_MISSING");
        else {
            if (!"ELIGIBLE".equals(selected.eligibilityResult())) failures.add("SELECTED_CANDIDATE_NOT_ELIGIBLE");
            String latestObs=latestObservationId(tenant,routing.bindingId());
            if (blank(selected.eligibilityObservationId()) || !selected.eligibilityObservationId().equals(latestObs)) failures.add("ELIGIBILITY_EVIDENCE_SUPERSEDED");
            else {
                EligibilityFreshness freshness=evaluateObservationFreshness(tenant,selected.eligibilityObservationId(),profile.maxObservationAgeSeconds());
                switch (freshness) {
                    case MISSING -> failures.add("ELIGIBILITY_EVIDENCE_MISSING");
                    case EXPIRED -> failures.add("ELIGIBILITY_EVIDENCE_EXPIRED");
                    case STALE -> failures.add("ELIGIBILITY_EVIDENCE_STALE");
                    case VALID -> { }
                }
            }
            if (!authorizationStillCurrent(tenant,selected.authorizationDecisionId())) failures.add("WHO_MAY_EVIDENCE_STALE");
        }
        return failures;
    }

    private boolean authorizationStillCurrent(String tenant,String decisionId) {
        try {
            Map<String,Object> row=jdbc.queryForMap("""
                    select d.result,d.selected_policy_id,d.selected_policy_version,p.version as current_version,p.status,p.effect
                    from delegation_authorization_decisions d left join delegation_policies p on p.tenant_id=d.tenant_id and p.policy_id=d.selected_policy_id
                    where d.tenant_id=:tenantId and d.decision_id=:decisionId
                    """,new MapSqlParameterSource("tenantId",tenant).addValue("decisionId",decisionId));
            return "PASS".equals(row.get("result")) && row.get("selected_policy_id")!=null && row.get("selected_policy_version")!=null
                    && ((Number)row.get("selected_policy_version")).intValue()==((Number)row.get("current_version")).intValue()
                    && "ACTIVE".equals(row.get("status")) && "ALLOW".equals(row.get("effect"));
        } catch (Exception ex) { return false; }
    }

    enum EligibilityFreshness { VALID, MISSING, EXPIRED, STALE }

    EligibilityFreshness evaluateObservationFreshness(String tenant,String observationId,int maxAge) {
        try {
            return jdbc.queryForObject(
                    "select observed_at,expires_at from provider_eligibility_observations where tenant_id=:tenantId and observation_id=:observationId",
                    new MapSqlParameterSource("tenantId",tenant).addValue("observationId",observationId),
                    (rs,rowNum)->{
                        OffsetDateTime observed=rs.getObject("observed_at",OffsetDateTime.class);
                        OffsetDateTime expires=rs.getObject("expires_at",OffsetDateTime.class);
                        if (observed==null) return EligibilityFreshness.MISSING;
                        OffsetDateTime now=OffsetDateTime.now();
                        if (expires!=null && !expires.isAfter(now)) return EligibilityFreshness.EXPIRED;
                        long ageSeconds=Duration.between(observed,now).getSeconds();
                        return ageSeconds>maxAge ? EligibilityFreshness.STALE : EligibilityFreshness.VALID;
                    });
        } catch (EmptyResultDataAccessException ex) {
            return EligibilityFreshness.MISSING;
        }
    }

    private String latestObservationId(String tenant,String bindingId) {
        try { return jdbc.queryForObject("select observation_id from provider_eligibility_observations where tenant_id=:tenantId and binding_id=:bindingId order by observed_at desc,observation_id desc limit 1",
                new MapSqlParameterSource("tenantId",tenant).addValue("bindingId",bindingId),String.class); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }

    private List<ExecutionAdapterRegistration> listActiveAdaptersForProvider(String tenant,String providerId) {
        return List.copyOf(jdbc.query("""
                select tenant_id,adapter_id,provider_id,provider_type,adapter_type,protocol,protocol_version,endpoint_ref,credential_ref,runtime_ref,
                       status,selection_priority,configuration_json,version,created_at,updated_at
                from execution_adapter_registrations where tenant_id=:tenantId and provider_id=:providerId and status='ACTIVE' order by selection_priority asc,adapter_id asc
                """,new MapSqlParameterSource("tenantId",tenant).addValue("providerId",providerId),new AdapterRowMapper()));
    }

    private ExecutionAdapterResolution persistResolution(String tenant,RoutingEvidence routing,ExecutionAdapterRegistration adapter,String resolutionMode,String result,List<String> reasons) {
        String id="execution-resolution-"+UUID.randomUUID(); OffsetDateTime now=OffsetDateTime.now();
        jdbc.update("""
                insert into execution_adapter_resolutions(tenant_id,resolution_id,resolution_mode,result,routing_decision_id,capability_code,operation,binding_id,provider_id,provider_type,
                  adapter_id,adapter_version,adapter_type,protocol,protocol_version,endpoint_ref,credential_ref,runtime_ref,reason_codes_json,resolved_at)
                values(:tenantId,:resolutionId,:resolutionMode,:result,:routingDecisionId,:capabilityCode,:operation,:bindingId,:providerId,:providerType,
                  :adapterId,:adapterVersion,:adapterType,:protocol,:protocolVersion,:endpointRef,:credentialRef,:runtimeRef,cast(:reasons as jsonb),:resolvedAt)
                """,new MapSqlParameterSource("tenantId",tenant).addValue("resolutionId",id).addValue("resolutionMode",resolutionMode).addValue("result",result).addValue("routingDecisionId",routing.decisionId())
                .addValue("capabilityCode",routing.capabilityCode()).addValue("operation",routing.operation()).addValue("bindingId",routing.bindingId()).addValue("providerId",routing.providerId())
                .addValue("providerType",routing.providerType()).addValue("adapterId",adapter==null?null:adapter.adapterId()).addValue("adapterVersion",adapter==null?null:adapter.version())
                .addValue("adapterType",adapter==null?null:adapter.adapterType()).addValue("protocol",adapter==null?null:adapter.protocol()).addValue("protocolVersion",adapter==null?null:adapter.protocolVersion())
                .addValue("endpointRef",adapter==null?null:adapter.endpointRef()).addValue("credentialRef",adapter==null?null:adapter.credentialRef()).addValue("runtimeRef",adapter==null?null:adapter.runtimeRef())
                .addValue("reasons",writeJson(reasons)).addValue("resolvedAt",now));
        return findResolution(tenant,id).orElseThrow();
    }

    private Optional<ExecutionAdapterResolution> findResolution(String tenant,String id) {
        try { return Optional.ofNullable(jdbc.queryForObject("""
                select tenant_id,resolution_id,resolution_mode,result,routing_decision_id,capability_code,operation,binding_id,provider_id,provider_type,
                       adapter_id,adapter_version,adapter_type,protocol,protocol_version,endpoint_ref,credential_ref,runtime_ref,reason_codes_json,resolved_at
                from execution_adapter_resolutions where tenant_id=:tenantId and resolution_id=:id
                """,new MapSqlParameterSource("tenantId",tenant).addValue("id",id),new ResolutionRowMapper())); }
        catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    private void appendVersion(String tenant,String adapterId,int version,String reason,OffsetDateTime now) {
        ExecutionAdapterRegistration current=findAdapter(tenant,adapterId).orElseThrow();
        jdbc.update("insert into execution_adapter_versions(tenant_id,adapter_id,version,snapshot_json,change_reason,actor_ref,created_at) values(:tenantId,:adapterId,:version,cast(:snapshot as jsonb),:reason,:actor,:createdAt)",
                new MapSqlParameterSource("tenantId",tenant).addValue("adapterId",adapterId).addValue("version",version).addValue("snapshot",writeJson(current)).addValue("reason",reason).addValue("actor",effectiveActorRef()).addValue("createdAt",now));
    }

    private ProviderIdentity loadProvider(String tenant,String providerId) {
        try { return jdbc.queryForObject("select provider_id,provider_type,provider_ref,catalog_status from capability_providers where tenant_id=:tenantId and provider_id=:providerId",
                new MapSqlParameterSource("tenantId",tenant).addValue("providerId",providerId),(rs,n)->new ProviderIdentity(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4))); }
        catch (EmptyResultDataAccessException ex) { throw new IllegalArgumentException("Capability Provider does not exist: "+providerId); }
    }

    private RoutingEvidence loadRoutingEvidence(String tenant,String id) {
        try { return jdbc.queryForObject("""
                select d.decision_id,d.decision_mode,d.result,d.capability_code,d.operation,d.routing_profile_id,d.routing_profile_version,d.selected_binding_id,d.selected_provider_id,d.candidates_json,
                       p.provider_type
                from provider_routing_decisions d left join capability_providers p on p.tenant_id=d.tenant_id and p.provider_id=d.selected_provider_id
                where d.tenant_id=:tenantId and d.decision_id=:id
                """,new MapSqlParameterSource("tenantId",tenant).addValue("id",id),(rs,n)->new RoutingEvidence(rs.getString("decision_id"),rs.getString("decision_mode"),rs.getString("result"),rs.getString("capability_code"),rs.getString("operation"),
                        rs.getString("routing_profile_id"),rs.getInt("routing_profile_version"),rs.getString("selected_binding_id"),rs.getString("selected_provider_id"),rs.getString("provider_type"),readCandidates(rs.getString("candidates_json")))); }
        catch (EmptyResultDataAccessException ex) { throw new IllegalArgumentException("Phase 4 routing decision does not exist: "+id); }
    }

    private CurrentBinding loadCurrentBinding(String tenant,String bindingId) {
        try { return jdbc.queryForObject("""
                select b.binding_id,b.capability_code,b.provider_id,p.provider_type,p.catalog_status,b.supported_operations_json,b.trust_status
                from capability_bindings b join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
                where b.tenant_id=:tenantId and b.binding_id=:bindingId
                """,new MapSqlParameterSource("tenantId",tenant).addValue("bindingId",bindingId),(rs,n)->new CurrentBinding(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),readStringList(rs.getString(6)),rs.getString(7))); }
        catch (EmptyResultDataAccessException ex) { throw new IllegalArgumentException("Capability Binding does not exist: "+bindingId); }
    }

    private ProfileState loadProfileState(String tenant,String profileId) {
        try { return jdbc.queryForObject("select version,status,max_observation_age_seconds from routing_profiles where tenant_id=:tenantId and profile_id=:profileId",
                new MapSqlParameterSource("tenantId",tenant).addValue("profileId",profileId),(rs,n)->new ProfileState(rs.getInt(1),rs.getString(2),rs.getInt(3))); }
        catch (EmptyResultDataAccessException ex) { return new ProfileState(-1,"MISSING",1); }
    }

    private void validateActiveAdapter(ProviderIdentity provider,String adapterType,String protocol,String endpointRef,String credentialRef,String runtimeRef) {
        if (!"REGISTERED".equals(provider.status())) throw new IllegalArgumentException("ACTIVE adapter requires a REGISTERED Capability Provider");
        if ("MANAGED_AGENT_NETTY".equals(adapterType)) {
            if (blank(runtimeRef)) throw new IllegalArgumentException("MANAGED_AGENT_NETTY requires runtimeRef");
            if (!blank(endpointRef)||!blank(credentialRef)) throw new IllegalArgumentException("MANAGED_AGENT_NETTY must use runtimeRef rather than endpointRef/credentialRef");
        } else {
            if (blank(endpointRef)) throw new IllegalArgumentException(adapterType+" requires endpointRef to an approved endpoint registry entry");
            if (blank(credentialRef)) throw new IllegalArgumentException(adapterType+" requires credentialRef to an approved machine-identity/credential registry entry");
        }
        if (!PROTOCOLS_BY_ADAPTER.get(adapterType).contains(protocol)) throw new IllegalArgumentException("protocol is not valid for adapterType "+adapterType);
    }

    private String normalizeProviderType(String v){String n=requireNonBlank(v,"providerType").toUpperCase(Locale.ROOT);if(!PROVIDER_TYPES.contains(n))throw new IllegalArgumentException("Unsupported providerType: "+n);return n;}
    private String normalizeAdapterType(String v){String n=requireNonBlank(v,"adapterType").toUpperCase(Locale.ROOT);if(!ADAPTER_TYPES.contains(n))throw new IllegalArgumentException("Unsupported adapterType: "+n);return n;}
    private String normalizeProtocol(String adapterType,String v){String n=requireNonBlank(v,"protocol").toUpperCase(Locale.ROOT);if(!PROTOCOLS_BY_ADAPTER.get(adapterType).contains(n))throw new IllegalArgumentException("Unsupported protocol for "+adapterType+": "+n);return n;}
    private String normalizeStatus(String v){String n=blank(v)?"DRAFT":v.trim().toUpperCase(Locale.ROOT);if(!STATUSES.contains(n))throw new IllegalArgumentException("Unsupported status: "+n);return n;}
    private void validateOpaqueReference(String v,String field){if(blank(v))return;String l=v.toLowerCase(Locale.ROOT);if(l.contains("http://")||l.contains("https://")||l.contains("bearer ")||l.contains("token=")||l.contains("password=")||v.contains("\n")||v.contains("\r"))throw new IllegalArgumentException(field+" must be an opaque registry reference, not a raw endpoint or secret");}
    private int normalizeLimit(int v){return Math.max(1,Math.min(v<=0?100:v,500));}
    private String requireTenant(String v){return requireNonBlank(v,"tenantId");}
    private String requireNonBlank(String v,String f){if(blank(v))throw new IllegalArgumentException(f+" is required");return v.trim();}
    private String firstNonBlank(String a,String b){return !blank(a)?a:b;}
    private String trimToNull(String v){return blank(v)?null:v.trim();}
    private boolean blank(String v){return v==null||v.isBlank();}

    private void bindDatabaseTenantContext(String tenantId) {
        String tenant=requireTenant(tenantId); IamTenantExecutionContext ctx=IamTenantContextHolder.current().orElse(null);
        if(ctx!=null&&!"INSTANCE".equalsIgnoreCase(ctx.tenantId())&&!tenant.equals(ctx.tenantId())) throw new IllegalArgumentException("Tenant context mismatch for Execution Adapter persistence");
        String actor=ctx==null||blank(ctx.actorId())?"execution-adapter":ctx.actorId();
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);
    }
    private String effectiveActorRef(){IamTenantExecutionContext c=IamTenantContextHolder.current().orElse(null);return c==null||blank(c.actorId())?"execution-adapter":c.actorId();}
    private String writeJson(Object v){try{return json.writeValueAsString(v==null?Map.of():v);}catch(Exception e){throw new IllegalArgumentException("Execution Adapter JSON cannot be serialized",e);}}
    private Map<String,Object> readMap(String v){try{return blank(v)?Map.of():json.readValue(v,OBJECT_MAP);}catch(Exception e){throw new IllegalStateException("Execution Adapter object cannot be read",e);}}
    private List<String> readStringList(String v){try{return blank(v)?List.of():json.readValue(v,STRING_LIST);}catch(Exception e){throw new IllegalStateException("Execution Adapter list cannot be read",e);}}
    private List<ProviderRoutingCandidateScore> readCandidates(String v){try{return blank(v)?List.of():json.readValue(v,CANDIDATE_LIST);}catch(Exception e){throw new IllegalStateException("Phase 4 candidate evidence cannot be read",e);}}

    private final class AdapterRowMapper implements RowMapper<ExecutionAdapterRegistration>{public ExecutionAdapterRegistration mapRow(ResultSet rs,int n)throws SQLException{return new ExecutionAdapterRegistration(rs.getString("tenant_id"),rs.getString("adapter_id"),rs.getString("provider_id"),rs.getString("provider_type"),rs.getString("adapter_type"),rs.getString("protocol"),rs.getString("protocol_version"),rs.getString("endpoint_ref"),rs.getString("credential_ref"),rs.getString("runtime_ref"),rs.getString("status"),rs.getInt("selection_priority"),readMap(rs.getString("configuration_json")),rs.getInt("version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));}}
    private final class ResolutionRowMapper implements RowMapper<ExecutionAdapterResolution>{public ExecutionAdapterResolution mapRow(ResultSet rs,int n)throws SQLException{return new ExecutionAdapterResolution(rs.getString("resolution_id"),rs.getString("tenant_id"),rs.getString("resolution_mode"),rs.getString("result"),rs.getString("routing_decision_id"),rs.getString("capability_code"),rs.getString("operation"),rs.getString("binding_id"),rs.getString("provider_id"),rs.getString("provider_type"),rs.getString("adapter_id"),(Integer)rs.getObject("adapter_version"),rs.getString("adapter_type"),rs.getString("protocol"),rs.getString("protocol_version"),rs.getString("endpoint_ref"),rs.getString("credential_ref"),rs.getString("runtime_ref"),readStringList(rs.getString("reason_codes_json")),rs.getObject("resolved_at",OffsetDateTime.class));}}
    private record ProviderIdentity(String providerId,String providerType,String providerRef,String status){}
    private record RoutingEvidence(String decisionId,String decisionMode,String result,String capabilityCode,String operation,String routingProfileId,int routingProfileVersion,String bindingId,String providerId,String providerType,List<ProviderRoutingCandidateScore> candidates){}
    private record CurrentBinding(String bindingId,String capabilityCode,String providerId,String providerType,String providerStatus,List<String> supportedOperations,String trustStatus){}
    private record ProfileState(int version,String status,int maxObservationAgeSeconds){}
}
