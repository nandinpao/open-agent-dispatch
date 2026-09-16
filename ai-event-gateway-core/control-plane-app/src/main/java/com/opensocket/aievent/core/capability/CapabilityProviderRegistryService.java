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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 2 WHO CAN authority.
 *
 * <p>This registry discovers and curates provider/capability supply only. It never performs
 * authorization, workload eligibility, provider ranking, provider selection or transport
 * selection. APPROVED is catalog trust only and is deliberately not runtime authority.</p>
 */
@Service
public class CapabilityProviderRegistryService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final Set<String> PROVIDER_TYPES = Set.of("MANAGED_AGENT", "REMOTE_A2A_AGENT", "MCP_TOOL", "INTERNAL_SERVICE");
    private static final Set<String> REGISTRATION_SOURCES = Set.of("MANUAL", "MANAGED_REGISTRY", "AGENT_CARD", "MCP_CATALOG", "INTERNAL_CATALOG");
    private static final Set<String> PROVIDER_STATUSES = Set.of("OBSERVED", "REGISTERED", "DISABLED", "RETIRED");
    private static final Set<String> TRUST_STATUSES = Set.of("DISCOVERED", "PROPOSED", "VERIFIED", "APPROVED", "SUSPENDED", "REVOKED", "STALE");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CapabilityProviderRegistryService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<CapabilityProvider> listProviders(String tenantId, String providerType, String catalogStatus, String search, String afterProviderId, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant).addValue("limit", normalizeLimit(limit));
        StringBuilder where = new StringBuilder(" where tenant_id=:tenantId");
        if (!blank(providerType)) {
            where.append(" and provider_type=:providerType");
            params.addValue("providerType", normalizeProviderType(providerType));
        }
        if (!blank(catalogStatus)) {
            where.append(" and catalog_status=:catalogStatus");
            params.addValue("catalogStatus", normalizeProviderStatus(catalogStatus));
        } else {
            where.append(" and catalog_status<>'RETIRED'");
        }
        if (!blank(search)) {
            where.append(" and (lower(provider_id) like :search or lower(display_name) like :search or lower(provider_ref) like :search)");
            params.addValue("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (!blank(afterProviderId)) {
            where.append(" and provider_id>:afterProviderId");
            params.addValue("afterProviderId", afterProviderId.trim());
        }
        return List.copyOf(jdbc.query("""
                select tenant_id,provider_id,provider_type,display_name,provider_ref,registration_source,
                       catalog_status,metadata_json,observed_at,last_verified_at,created_at,updated_at
                from capability_providers
                """ + where + " order by provider_id asc limit :limit", params, new ProviderRowMapper()));
    }

    @Transactional(readOnly = true)
    public Optional<CapabilityProvider> findProvider(String tenantId, String providerId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select tenant_id,provider_id,provider_type,display_name,provider_ref,registration_source,
                           catalog_status,metadata_json,observed_at,last_verified_at,created_at,updated_at
                    from capability_providers where tenant_id=:tenantId and provider_id=:providerId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("providerId", requireNonBlank(providerId, "providerId")), new ProviderRowMapper()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public CapabilityProvider upsertProvider(String tenantId, String pathProviderId, CapabilityProvider request) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null) throw new IllegalArgumentException("Capability Provider request body is required");
        String providerId = requireNonBlank(firstNonBlank(pathProviderId, request.providerId()), "providerId");
        if (!blank(request.providerId()) && !providerId.equals(request.providerId().trim())) {
            throw new IllegalArgumentException("providerId in the request body must match the path");
        }
        String providerType = normalizeProviderType(request.providerType());
        String displayName = requireNonBlank(request.displayName(), "displayName");
        String providerRef = requireNonBlank(request.providerRef(), "providerRef");
        rejectExecutionDetails(providerRef, "providerRef");
        String source = normalizeRegistrationSource(request.registrationSource());
        String status = normalizeProviderStatus(request.catalogStatus());
        findProviderIdByIdentity(tenant, providerType, providerRef).ifPresent(conflictingProviderId -> {
            if (!conflictingProviderId.equals(providerId)) {
                throw new IllegalArgumentException("Provider identity is already registered as: " + conflictingProviderId);
            }
        });
        OffsetDateTime now = OffsetDateTime.now();
        CapabilityProvider existing = findProvider(tenant, providerId).orElse(null);
        // Self/discovery sources cannot auto-register on first observation. A later authenticated
        // administrator update may explicitly promote OBSERVED -> REGISTERED after verification.
        if (existing == null && ("AGENT_CARD".equals(source) || "MCP_CATALOG".equals(source)) && "REGISTERED".equals(status)) {
            status = "OBSERVED";
        }
        if (existing != null) {
            if (!existing.providerType().equals(providerType) || !existing.providerRef().equals(providerRef) || !existing.registrationSource().equals(source)) {
                throw new IllegalArgumentException("Provider identity fields providerType/providerRef/registrationSource are immutable; register a new provider identity instead");
            }
        }
        OffsetDateTime createdAt = existing == null ? now : existing.createdAt();
        jdbc.update("""
                insert into capability_providers(
                  tenant_id,provider_id,provider_type,display_name,provider_ref,registration_source,catalog_status,
                  metadata_json,observed_at,last_verified_at,created_at,updated_at)
                values(:tenantId,:providerId,:providerType,:displayName,:providerRef,:registrationSource,:catalogStatus,
                  cast(:metadataJson as jsonb),:observedAt,:lastVerifiedAt,:createdAt,:updatedAt)
                on conflict(tenant_id,provider_id) do update set
                  provider_type=excluded.provider_type,
                  display_name=excluded.display_name,
                  provider_ref=excluded.provider_ref,
                  registration_source=excluded.registration_source,
                  catalog_status=excluded.catalog_status,
                  metadata_json=excluded.metadata_json,
                  observed_at=coalesce(excluded.observed_at,capability_providers.observed_at),
                  last_verified_at=coalesce(excluded.last_verified_at,capability_providers.last_verified_at),
                  updated_at=excluded.updated_at
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenant).addValue("providerId", providerId).addValue("providerType", providerType)
                .addValue("displayName", displayName).addValue("providerRef", providerRef).addValue("registrationSource", source)
                .addValue("catalogStatus", status).addValue("metadataJson", writeJson(request.metadata()))
                .addValue("observedAt", request.observedAt()).addValue("lastVerifiedAt", request.lastVerifiedAt())
                .addValue("createdAt", createdAt).addValue("updatedAt", now));
        return findProvider(tenant, providerId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<CapabilityBinding> listBindings(String tenantId, String capabilityCode, String providerId, String trustStatus, String afterBindingId, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant).addValue("limit", normalizeLimit(limit));
        StringBuilder where = new StringBuilder(" where b.tenant_id=:tenantId");
        if (!blank(capabilityCode)) {
            where.append(" and b.capability_code=:capabilityCode");
            params.addValue("capabilityCode", capabilityCode.trim().toLowerCase(Locale.ROOT));
        }
        if (!blank(providerId)) {
            where.append(" and b.provider_id=:providerId");
            params.addValue("providerId", providerId.trim());
        }
        if (!blank(trustStatus)) {
            where.append(" and b.trust_status=:trustStatus");
            params.addValue("trustStatus", normalizeTrustStatus(trustStatus));
        } else {
            where.append(" and b.trust_status<>'REVOKED'");
        }
        if (!blank(afterBindingId)) {
            where.append(" and b.binding_id>:afterBindingId");
            params.addValue("afterBindingId", afterBindingId.trim());
        }
        return List.copyOf(jdbc.query("""
                select b.tenant_id,b.binding_id,b.capability_code,b.provider_id,p.provider_type,p.display_name as provider_display_name,
                       b.supported_operations_json,b.trust_status,b.source_revision,b.verification_method,b.verification_evidence_ref,
                       b.observed_at,b.verified_at,b.approved_at,b.stale_after,b.created_at,b.updated_at
                from capability_bindings b
                join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
                """ + where + " order by b.binding_id asc limit :limit", params, new BindingRowMapper()));
    }

    @Transactional(readOnly = true)
    public Optional<CapabilityBinding> findBinding(String tenantId, String bindingId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select b.tenant_id,b.binding_id,b.capability_code,b.provider_id,p.provider_type,p.display_name as provider_display_name,
                           b.supported_operations_json,b.trust_status,b.source_revision,b.verification_method,b.verification_evidence_ref,
                           b.observed_at,b.verified_at,b.approved_at,b.stale_after,b.created_at,b.updated_at
                    from capability_bindings b
                    join capability_providers p on p.tenant_id=b.tenant_id and p.provider_id=b.provider_id
                    where b.tenant_id=:tenantId and b.binding_id=:bindingId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("bindingId", requireNonBlank(bindingId, "bindingId")), new BindingRowMapper()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public CapabilityBinding upsertBinding(String tenantId, String pathBindingId, CapabilityBinding request, String actorRef, String reason) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null) throw new IllegalArgumentException("Capability Binding request body is required");
        String bindingId = blank(pathBindingId) ? firstNonBlank(request.bindingId(), "binding-" + UUID.randomUUID()) : pathBindingId.trim();
        if (!blank(request.bindingId()) && !bindingId.equals(request.bindingId().trim())) {
            throw new IllegalArgumentException("bindingId in the request body must match the path");
        }
        String capabilityCode = requireNonBlank(request.capabilityCode(), "capabilityCode").toLowerCase(Locale.ROOT);
        CapabilityDefinition capability = requireCapability(tenant, capabilityCode);
        CapabilityProvider provider = findProvider(tenant, requireNonBlank(request.providerId(), "providerId"))
                .orElseThrow(() -> new IllegalArgumentException("Provider does not exist: " + request.providerId()));
        if ("DISABLED".equals(provider.catalogStatus()) || "RETIRED".equals(provider.catalogStatus())) {
            throw new IllegalArgumentException("Provider must be OBSERVED or REGISTERED before it can supply a Capability Binding");
        }
        List<String> operations = normalizeOperations(request.supportedOperations());
        if (operations.isEmpty()) operations = capability.operations();
        if (!new LinkedHashSet<>(capability.operations()).containsAll(operations)) {
            throw new IllegalArgumentException("supportedOperations must be a subset of the Canonical Capability operations");
        }

        CapabilityBinding existing = findBinding(tenant, bindingId).orElse(null);
        if (existing == null) {
            findBindingIdByPair(tenant, capabilityCode, provider.providerId()).ifPresent(conflictingBindingId -> {
                throw new IllegalArgumentException("Capability/Provider binding already exists as: " + conflictingBindingId);
            });
        }
        String targetTrust = normalizeTrustStatus(request.trustStatus());
        if (existing != null) {
            if (!existing.capabilityCode().equals(capabilityCode) || !existing.providerId().equals(provider.providerId())) {
                throw new IllegalArgumentException("Capability Binding identity (capabilityCode/providerId) is immutable; create a new binding instead");
            }
            boolean claimChanged = !existing.supportedOperations().equals(operations)
                    || !java.util.Objects.equals(existing.sourceRevision(), trimToNull(request.sourceRevision()));
            if (claimChanged && !Set.of("DISCOVERED", "PROPOSED", "STALE").contains(existing.trustStatus())) {
                throw new IllegalArgumentException("A verified/approved binding claim must be marked STALE before changing operations or source revision");
            }
        }
        if (existing != null && !existing.trustStatus().equals(targetTrust) && blank(reason)) {
            throw new IllegalArgumentException("A change reason is required for Capability Binding trust transitions");
        }
        if (existing == null) {
            if (!Set.of("DISCOVERED", "PROPOSED").contains(targetTrust)) {
                throw new IllegalArgumentException("A new Capability Binding must start as DISCOVERED or PROPOSED");
            }
            if (("AGENT_CARD".equals(provider.registrationSource()) || "MCP_CATALOG".equals(provider.registrationSource())) && !"DISCOVERED".equals(targetTrust)) {
                throw new IllegalArgumentException("Self/discovery supplied bindings must begin as DISCOVERED and cannot auto-approve themselves");
            }
        } else {
            validateTransition(existing.trustStatus(), targetTrust);
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime verifiedAt = request.verifiedAt();
        OffsetDateTime approvedAt = request.approvedAt();
        if ("VERIFIED".equals(targetTrust) && verifiedAt == null) verifiedAt = now;
        if ("APPROVED".equals(targetTrust)) {
            if (verifiedAt == null) verifiedAt = existing == null ? null : existing.verifiedAt();
            if (verifiedAt == null) throw new IllegalArgumentException("Binding must be VERIFIED before APPROVED");
            if (approvedAt == null) approvedAt = now;
        }
        OffsetDateTime createdAt = existing == null ? now : existing.createdAt();

        jdbc.update("""
                insert into capability_bindings(
                  tenant_id,binding_id,capability_code,provider_id,supported_operations_json,trust_status,source_revision,
                  verification_method,verification_evidence_ref,observed_at,verified_at,approved_at,stale_after,created_at,updated_at)
                values(:tenantId,:bindingId,:capabilityCode,:providerId,cast(:operationsJson as jsonb),:trustStatus,:sourceRevision,
                  :verificationMethod,:verificationEvidenceRef,:observedAt,:verifiedAt,:approvedAt,:staleAfter,:createdAt,:updatedAt)
                on conflict(tenant_id,binding_id) do update set
                  capability_code=excluded.capability_code,
                  provider_id=excluded.provider_id,
                  supported_operations_json=excluded.supported_operations_json,
                  trust_status=excluded.trust_status,
                  source_revision=excluded.source_revision,
                  verification_method=excluded.verification_method,
                  verification_evidence_ref=excluded.verification_evidence_ref,
                  observed_at=coalesce(excluded.observed_at,capability_bindings.observed_at),
                  verified_at=coalesce(excluded.verified_at,capability_bindings.verified_at),
                  approved_at=coalesce(excluded.approved_at,capability_bindings.approved_at),
                  stale_after=excluded.stale_after,
                  updated_at=excluded.updated_at
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenant).addValue("bindingId", bindingId).addValue("capabilityCode", capabilityCode)
                .addValue("providerId", provider.providerId()).addValue("operationsJson", writeJson(operations)).addValue("trustStatus", targetTrust)
                .addValue("sourceRevision", trimToNull(request.sourceRevision())).addValue("verificationMethod", trimToNull(request.verificationMethod()))
                .addValue("verificationEvidenceRef", trimToNull(request.verificationEvidenceRef())).addValue("observedAt", request.observedAt())
                .addValue("verifiedAt", verifiedAt).addValue("approvedAt", approvedAt).addValue("staleAfter", request.staleAfter())
                .addValue("createdAt", createdAt).addValue("updatedAt", now));

        String previous = existing == null ? null : existing.trustStatus();
        if (existing == null || !targetTrust.equals(previous)) {
            appendTrustEvent(tenant, bindingId, previous, targetTrust, reason, actorRef, now);
        }
        return findBinding(tenant, bindingId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<ManagedAgentProviderLink> findManagedAgentProviderLink(String tenantId, String providerId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        String provider = requireNonBlank(providerId, "providerId");
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select tenant_id,provider_id,agent_id,status,link_source,verified_at,created_by,created_at,updated_at
                    from managed_agent_provider_links
                    where tenant_id=:tenantId and provider_id=:providerId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("providerId", provider),
                    (rs, rowNum) -> new ManagedAgentProviderLink(rs.getString("tenant_id"), rs.getString("provider_id"),
                            rs.getString("agent_id"), rs.getString("status"), rs.getString("link_source"),
                            rs.getObject("verified_at", OffsetDateTime.class), rs.getString("created_by"),
                            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class))));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public ManagedAgentProviderLink upsertManagedAgentProviderLink(String tenantId, String providerId, ManagedAgentProviderLink request, String actorRef) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        String provider = requireNonBlank(providerId, "providerId");
        if (request == null) throw new IllegalArgumentException("Managed Agent Provider Link request body is required");
        if (!blank(request.providerId()) && !provider.equals(request.providerId().trim())) {
            throw new IllegalArgumentException("providerId in the request body must match the path");
        }
        CapabilityProvider providerRecord = findProvider(tenant, provider)
                .orElseThrow(() -> new IllegalArgumentException("Capability Provider does not exist: " + provider));
        if (!"MANAGED_AGENT".equals(providerRecord.providerType())) {
            throw new IllegalArgumentException("Managed Provider Link requires providerType=MANAGED_AGENT");
        }
        if (!"REGISTERED".equals(providerRecord.catalogStatus())) {
            throw new IllegalArgumentException("Managed Agent Provider must be REGISTERED before linking to an Agent");
        }
        String agentId = requireNonBlank(request.agentId(), "agentId");
        Integer agentCount = jdbc.queryForObject("""
                select count(*) from agent_profiles
                where tenant_id=:tenantId and agent_id=:agentId and approval_status='APPROVED' and enabled=true
                """, new MapSqlParameterSource("tenantId", tenant).addValue("agentId", agentId), Integer.class);
        if (agentCount == null || agentCount != 1) {
            throw new IllegalArgumentException("Managed Agent Provider Link requires an existing APPROVED and enabled Agent: " + agentId);
        }
        String status = blank(request.status()) ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "SUSPENDED", "RETIRED").contains(status)) {
            throw new IllegalArgumentException("Unsupported Managed Agent Provider Link status: " + status);
        }
        String linkSource = blank(request.linkSource()) ? "MANUAL" : request.linkSource().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MANUAL", "EXACT_AGENT_PROFILE_REF", "FLOW_MIGRATION_REVIEW").contains(linkSource)) {
            throw new IllegalArgumentException("Unsupported Managed Agent Provider Link source: " + linkSource);
        }
        Optional<ManagedAgentProviderLink> existing = findManagedAgentProviderLink(tenant, provider);
        if (existing.isPresent() && !existing.get().agentId().equals(agentId)) {
            throw new IllegalArgumentException("Managed Agent Provider Link identity is immutable; retire the existing Provider link before relinking");
        }
        try {
            String conflicting = jdbc.queryForObject("""
                    select provider_id from managed_agent_provider_links
                    where tenant_id=:tenantId and agent_id=:agentId and provider_id<>:providerId and status<>'RETIRED'
                    limit 1
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("agentId", agentId).addValue("providerId", provider), String.class);
            if (!blank(conflicting)) throw new IllegalArgumentException("Agent is already linked to another Managed Provider: " + conflicting);
        } catch (EmptyResultDataAccessException ignored) {
            // no conflicting active identity link
        }
        OffsetDateTime now = OffsetDateTime.now();
        String actor = effectiveActorRef(actorRef);
        jdbc.update("""
                insert into managed_agent_provider_links(tenant_id,provider_id,agent_id,status,link_source,verified_at,created_by,created_at,updated_at)
                values(:tenantId,:providerId,:agentId,:status,:linkSource,:verifiedAt,:createdBy,:createdAt,:updatedAt)
                on conflict(tenant_id,provider_id) do update set
                  status=excluded.status,
                  link_source=excluded.link_source,
                  verified_at=excluded.verified_at,
                  updated_at=excluded.updated_at
                """, new MapSqlParameterSource("tenantId", tenant).addValue("providerId", provider).addValue("agentId", agentId)
                .addValue("status", status).addValue("linkSource", linkSource)
                .addValue("verifiedAt", "ACTIVE".equals(status) ? (request.verifiedAt() == null ? now : request.verifiedAt()) : request.verifiedAt())
                .addValue("createdBy", actor).addValue("createdAt", existing.map(ManagedAgentProviderLink::createdAt).orElse(now)).addValue("updatedAt", now));
        return findManagedAgentProviderLink(tenant, provider).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<CapabilityBindingTrustEvent> trustEvents(String tenantId, String bindingId) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        return List.copyOf(jdbc.query("""
                select tenant_id,event_id,binding_id,from_status,to_status,reason,actor_ref,occurred_at
                from capability_binding_trust_events
                where tenant_id=:tenantId and binding_id=:bindingId
                order by occurred_at asc,event_id asc
                """, new MapSqlParameterSource("tenantId", tenant).addValue("bindingId", requireNonBlank(bindingId, "bindingId")),
                (rs, rowNum) -> new CapabilityBindingTrustEvent(rs.getString("tenant_id"), rs.getString("event_id"), rs.getString("binding_id"),
                        rs.getString("from_status"), rs.getString("to_status"), rs.getString("reason"), rs.getString("actor_ref"),
                        rs.getObject("occurred_at", OffsetDateTime.class))));
    }

    private CapabilityDefinition requireCapability(String tenant, String capabilityCode) {
        try {
            return jdbc.queryForObject("""
                    select tenant_id,capability_id,capability_code,display_name,description,semantic_domain,category,capability_type,
                           operations_json,input_schema_json,output_schema_json,resource_types_json,data_classes_json,version,status,
                           created_at,updated_at
                    from capability_definitions where tenant_id=:tenantId and capability_code=:capabilityCode and status<>'RETIRED'
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("capabilityCode", capabilityCode), (rs, rowNum) ->
                    new CapabilityDefinition(rs.getString("tenant_id"), rs.getString("capability_id"), rs.getString("capability_code"),
                            rs.getString("display_name"), rs.getString("description"), rs.getString("semantic_domain"), rs.getString("category"),
                            rs.getString("capability_type"), readStringList(rs.getString("operations_json")), readMap(rs.getString("input_schema_json")),
                            readMap(rs.getString("output_schema_json")), readStringList(rs.getString("resource_types_json")),
                            readStringList(rs.getString("data_classes_json")), rs.getInt("version"), rs.getString("status"), List.of(),
                            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Canonical Capability does not exist: " + capabilityCode);
        }
    }

    private Optional<String> findProviderIdByIdentity(String tenant, String providerType, String providerRef) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select provider_id from capability_providers
                    where tenant_id=:tenantId and provider_type=:providerType and provider_ref=:providerRef
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("providerType", providerType).addValue("providerRef", providerRef), String.class));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> findBindingIdByPair(String tenant, String capabilityCode, String providerId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select binding_id from capability_bindings
                    where tenant_id=:tenantId and capability_code=:capabilityCode and provider_id=:providerId
                    """, new MapSqlParameterSource("tenantId", tenant).addValue("capabilityCode", capabilityCode).addValue("providerId", providerId), String.class));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private void appendTrustEvent(String tenant, String bindingId, String fromStatus, String toStatus, String reason, String actorRef, OffsetDateTime now) {
        jdbc.update("""
                insert into capability_binding_trust_events(tenant_id,event_id,binding_id,from_status,to_status,reason,actor_ref,occurred_at)
                values(:tenantId,:eventId,:bindingId,:fromStatus,:toStatus,:reason,:actorRef,:occurredAt)
                """, new MapSqlParameterSource("tenantId", tenant).addValue("eventId", "binding-trust-" + UUID.randomUUID())
                .addValue("bindingId", bindingId).addValue("fromStatus", fromStatus).addValue("toStatus", toStatus)
                .addValue("reason", trimToNull(reason)).addValue("actorRef", effectiveActorRef(actorRef)).addValue("occurredAt", now));
    }

    private void validateTransition(String from, String to) {
        if (from.equals(to)) return;
        Map<String, Set<String>> allowed = Map.of(
                "DISCOVERED", Set.of("PROPOSED", "SUSPENDED", "REVOKED", "STALE"),
                "PROPOSED", Set.of("VERIFIED", "SUSPENDED", "REVOKED", "STALE"),
                "VERIFIED", Set.of("APPROVED", "SUSPENDED", "REVOKED", "STALE"),
                "APPROVED", Set.of("SUSPENDED", "REVOKED", "STALE"),
                "SUSPENDED", Set.of("PROPOSED", "VERIFIED", "APPROVED", "REVOKED", "STALE"),
                "STALE", Set.of("PROPOSED", "VERIFIED", "SUSPENDED", "REVOKED"),
                "REVOKED", Set.of());
        if (!allowed.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalArgumentException("Unsupported Capability Binding trust transition: " + from + " -> " + to);
        }
    }

    private void rejectExecutionDetails(String value, String field) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.contains("token=") || lower.contains("password=") || lower.contains("credential=")) {
            throw new IllegalArgumentException(field + " must be an opaque provider identity, not endpoint or credential data");
        }
    }

    private String normalizeProviderType(String value) {
        String normalized = requireNonBlank(value, "providerType").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!PROVIDER_TYPES.contains(normalized)) throw new IllegalArgumentException("Unsupported providerType: " + normalized);
        return normalized;
    }

    private String normalizeRegistrationSource(String value) {
        String normalized = blank(value) ? "MANUAL" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!REGISTRATION_SOURCES.contains(normalized)) throw new IllegalArgumentException("Unsupported registrationSource: " + normalized);
        return normalized;
    }

    private String normalizeProviderStatus(String value) {
        String normalized = blank(value) ? "REGISTERED" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!PROVIDER_STATUSES.contains(normalized)) throw new IllegalArgumentException("Unsupported catalogStatus: " + normalized);
        return normalized;
    }

    private String normalizeTrustStatus(String value) {
        String normalized = blank(value) ? "DISCOVERED" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!TRUST_STATUSES.contains(normalized)) throw new IllegalArgumentException("Unsupported trustStatus: " + normalized);
        return normalized;
    }

    private List<String> normalizeOperations(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.stream().filter(value -> !blank(value)).map(value -> value.trim().toUpperCase(Locale.ROOT).replace(' ', '_')).forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private void bindDatabaseTenantContext(String tenantId) {
        String tenant = requireTenant(tenantId);
        IamTenantExecutionContext requestContext = IamTenantContextHolder.current().orElse(null);
        if (requestContext != null && !"INSTANCE".equalsIgnoreCase(requestContext.tenantId()) && !tenant.equals(requestContext.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for Capability Provider persistence");
        }
        String actor = requestContext == null || blank(requestContext.actorId()) ? "capability-provider-registry" : requestContext.actorId();
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }

    private String effectiveActorRef(String requested) {
        if (!blank(requested)) return requested.trim();
        IamTenantExecutionContext context = IamTenantContextHolder.current().orElse(null);
        return context == null || blank(context.actorId()) ? "capability-provider-registry" : context.actorId();
    }

    private String requireTenant(String value) { return requireNonBlank(value, "tenantId"); }
    private int normalizeLimit(int value) { return Math.max(1, Math.min(value <= 0 ? 200 : value, 500)); }
    private String requireNonBlank(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private String firstNonBlank(String first, String second) { return !blank(first) ? first : second; }
    private String trimToNull(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { throw new IllegalArgumentException("Provider metadata cannot be serialized", ex); }
    }
    private List<String> readStringList(String value) {
        try { return blank(value) ? List.of() : json.readValue(value, STRING_LIST); }
        catch (Exception ex) { throw new IllegalStateException("Capability Binding operations cannot be read", ex); }
    }
    private Map<String, Object> readMap(String value) {
        try { return blank(value) ? Map.of() : json.readValue(value, OBJECT_MAP); }
        catch (Exception ex) { throw new IllegalStateException("Provider metadata cannot be read", ex); }
    }

    private final class ProviderRowMapper implements RowMapper<CapabilityProvider> {
        @Override
        public CapabilityProvider mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CapabilityProvider(rs.getString("tenant_id"), rs.getString("provider_id"), rs.getString("provider_type"),
                    rs.getString("display_name"), rs.getString("provider_ref"), rs.getString("registration_source"), rs.getString("catalog_status"),
                    readMap(rs.getString("metadata_json")), rs.getObject("observed_at", OffsetDateTime.class), rs.getObject("last_verified_at", OffsetDateTime.class),
                    rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
        }
    }

    private final class BindingRowMapper implements RowMapper<CapabilityBinding> {
        @Override
        public CapabilityBinding mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CapabilityBinding(rs.getString("tenant_id"), rs.getString("binding_id"), rs.getString("capability_code"),
                    rs.getString("provider_id"), rs.getString("provider_type"), rs.getString("provider_display_name"),
                    readStringList(rs.getString("supported_operations_json")), rs.getString("trust_status"), rs.getString("source_revision"),
                    rs.getString("verification_method"), rs.getString("verification_evidence_ref"), rs.getObject("observed_at", OffsetDateTime.class),
                    rs.getObject("verified_at", OffsetDateTime.class), rs.getObject("approved_at", OffsetDateTime.class), rs.getObject("stale_after", OffsetDateTime.class),
                    rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
        }
    }
}
