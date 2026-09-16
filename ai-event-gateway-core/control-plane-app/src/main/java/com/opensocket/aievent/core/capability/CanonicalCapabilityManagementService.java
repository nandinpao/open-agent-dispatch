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
 * Phase 1 canonical Capability catalog authority.
 *
 * <p>The catalog is intentionally provider-neutral and protocol-neutral. No method in
 * this service accepts or persists target system, target Domain, Agent Pool, Agent ID,
 * endpoint, credential, A2A transport or MCP transport information.</p>
 */
@Service
public class CanonicalCapabilityManagementService {
    private static final Pattern CAPABILITY_CODE = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+$");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED", "RETIRED");
    private static final String SELECT_COLUMNS = "tenant_id,capability_id,capability_code,display_name,description,semantic_domain,category,capability_type,operations_json,input_schema_json,output_schema_json,resource_types_json,data_classes_json,version,status,created_at,updated_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public CanonicalCapabilityManagementService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<CapabilityDefinition> list(String tenantId, String status, String search, String serviceCode, int limit) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenant)
                .addValue("limit", normalizeLimit(limit));
        StringBuilder where = new StringBuilder(" where c.tenant_id=:tenantId");
        if (!blank(status)) {
            where.append(" and c.status=:status");
            params.addValue("status", normalizeStatus(status));
        } else {
            where.append(" and c.status<>'RETIRED'");
        }
        if (!blank(search)) {
            where.append(" and (lower(c.capability_code) like :search or lower(c.display_name) like :search or lower(coalesce(c.description,'')) like :search)");
            params.addValue("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (!blank(serviceCode)) {
            where.append(" and exists (select 1 from service_code_capability_mappings m where m.tenant_id=c.tenant_id and m.capability_code=c.capability_code and m.service_code=:serviceCode and m.status='ACTIVE')");
            params.addValue("serviceCode", normalizeServiceCode(serviceCode));
        }
        List<CapabilityDefinition> result = jdbc.query(
                "select " + SELECT_COLUMNS + " from capability_definitions c" + where + " order by c.capability_code asc limit :limit",
                params, new CapabilityRowMapper());
        return attachServiceCodes(tenant, result);
    }

    @Transactional(readOnly = true)
    public Optional<CapabilityDefinition> find(String tenantId, String capabilityCode) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        String code = normalizeCapabilityCode(capabilityCode);
        try {
            CapabilityDefinition definition = jdbc.queryForObject(
                    "select " + SELECT_COLUMNS + " from capability_definitions c where c.tenant_id=:tenantId and c.capability_code=:capabilityCode",
                    new MapSqlParameterSource().addValue("tenantId", tenant).addValue("capabilityCode", code),
                    new CapabilityRowMapper());
            return Optional.ofNullable(definition == null ? null : withServiceCodes(definition, serviceCodes(tenant, code)));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Transactional
    public CapabilityDefinition upsert(String tenantId, String pathCapabilityCode, CapabilityDefinition request) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        if (request == null) throw new IllegalArgumentException("Capability Definition request body is required");
        String code = normalizeCapabilityCode(firstNonBlank(pathCapabilityCode, request.capabilityCode()));
        if (!blank(request.capabilityCode()) && !code.equals(normalizeCapabilityCode(request.capabilityCode()))) {
            throw new IllegalArgumentException("capabilityCode in the request body must match the path");
        }
        String displayName = requireNonBlank(request.displayName(), "displayName");
        String status = normalizeStatus(request.status());
        List<String> operations = normalizeUpperList(request.operations());
        if ("ACTIVE".equals(status) && operations.isEmpty()) {
            throw new IllegalArgumentException("At least one operation is required before a Capability can become ACTIVE");
        }
        List<String> resourceTypes = normalizeUpperList(request.resourceTypes());
        List<String> dataClasses = normalizeUpperList(request.dataClasses());
        List<String> serviceCodes = normalizeServiceCodes(request.serviceCodes());
        String semanticDomain = normalizeTaxonomy(request.semanticDomain());
        String category = normalizeTaxonomy(request.category());
        String capabilityType = normalizeType(request.capabilityType());
        OffsetDateTime now = OffsetDateTime.now();

        ExistingRevision existing = findRevision(tenant, code).orElse(null);
        String id = existing == null ? "capability-" + UUID.randomUUID() : existing.capabilityId();
        int version = existing == null ? 1 : existing.version() + 1;
        OffsetDateTime createdAt = existing == null ? now : existing.createdAt();

        jdbc.update("""
                insert into capability_definitions(
                  tenant_id,capability_id,capability_code,display_name,description,semantic_domain,category,capability_type,
                  operations_json,input_schema_json,output_schema_json,resource_types_json,data_classes_json,version,status,created_at,updated_at)
                values(
                  :tenantId,:capabilityId,:capabilityCode,:displayName,:description,:semanticDomain,:category,:capabilityType,
                  cast(:operationsJson as jsonb),cast(:inputSchemaJson as jsonb),cast(:outputSchemaJson as jsonb),cast(:resourceTypesJson as jsonb),cast(:dataClassesJson as jsonb),:version,:status,:createdAt,:updatedAt)
                on conflict(tenant_id,capability_code) do update set
                  display_name=excluded.display_name,
                  description=excluded.description,
                  semantic_domain=excluded.semantic_domain,
                  category=excluded.category,
                  capability_type=excluded.capability_type,
                  operations_json=excluded.operations_json,
                  input_schema_json=excluded.input_schema_json,
                  output_schema_json=excluded.output_schema_json,
                  resource_types_json=excluded.resource_types_json,
                  data_classes_json=excluded.data_classes_json,
                  version=excluded.version,
                  status=excluded.status,
                  updated_at=excluded.updated_at
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenant)
                .addValue("capabilityId", id)
                .addValue("capabilityCode", code)
                .addValue("displayName", displayName)
                .addValue("description", trimToNull(request.description()))
                .addValue("semanticDomain", semanticDomain)
                .addValue("category", category)
                .addValue("capabilityType", capabilityType)
                .addValue("operationsJson", writeJson(operations))
                .addValue("inputSchemaJson", writeJson(safeMap(request.inputSchema())))
                .addValue("outputSchemaJson", writeJson(safeMap(request.outputSchema())))
                .addValue("resourceTypesJson", writeJson(resourceTypes))
                .addValue("dataClassesJson", writeJson(dataClasses))
                .addValue("version", version)
                .addValue("status", status)
                .addValue("createdAt", createdAt)
                .addValue("updatedAt", now));

        replaceServiceCodeMappings(tenant, code, serviceCodes, now);
        return find(tenant, code).orElseThrow();
    }

    /**
     * Resolves an already-known Service Code to semantic requirements only.
     * Provider discovery/routing is intentionally not part of Phase 1.
     */
    @Transactional(readOnly = true)
    public List<CapabilityRequirement> requirementsForServiceCode(String tenantId, String serviceCode) {
        String tenant = requireTenant(tenantId);
        bindDatabaseTenantContext(tenant);
        String normalizedServiceCode = normalizeServiceCode(serviceCode);
        List<CapabilityDefinition> definitions = list(tenant, "ACTIVE", null, normalizedServiceCode, 500);
        List<CapabilityRequirement> requirements = new ArrayList<>();
        for (CapabilityDefinition definition : definitions) {
            String operation = definition.operations().size() == 1 ? definition.operations().getFirst() : null;
            requirements.add(new CapabilityRequirement(
                    definition.capabilityCode(), operation, Map.of(), Map.of(),
                    firstOrNull(definition.dataClasses()), null, null, "BALANCED"));
        }
        return List.copyOf(requirements);
    }

    private List<CapabilityDefinition> attachServiceCodes(String tenant, List<CapabilityDefinition> definitions) {
        if (definitions.isEmpty()) return definitions;
        List<CapabilityDefinition> result = new ArrayList<>(definitions.size());
        for (CapabilityDefinition definition : definitions) {
            result.add(withServiceCodes(definition, serviceCodes(tenant, definition.capabilityCode())));
        }
        return List.copyOf(result);
    }

    private CapabilityDefinition withServiceCodes(CapabilityDefinition d, List<String> serviceCodes) {
        return new CapabilityDefinition(d.tenantId(), d.capabilityId(), d.capabilityCode(), d.displayName(), d.description(),
                d.semanticDomain(), d.category(), d.capabilityType(), d.operations(), d.inputSchema(), d.outputSchema(),
                d.resourceTypes(), d.dataClasses(), d.version(), d.status(), serviceCodes, d.createdAt(), d.updatedAt());
    }

    private void replaceServiceCodeMappings(String tenant, String capabilityCode, List<String> serviceCodes, OffsetDateTime now) {
        jdbc.update("delete from service_code_capability_mappings where tenant_id=:tenantId and capability_code=:capabilityCode",
                new MapSqlParameterSource().addValue("tenantId", tenant).addValue("capabilityCode", capabilityCode));
        for (String serviceCode : serviceCodes) {
            jdbc.update("""
                    insert into service_code_capability_mappings(
                      tenant_id,service_code,capability_code,requirement_defaults_json,status,created_at,updated_at)
                    values(:tenantId,:serviceCode,:capabilityCode,'{}'::jsonb,'ACTIVE',:createdAt,:updatedAt)
                    """, new MapSqlParameterSource()
                    .addValue("tenantId", tenant)
                    .addValue("serviceCode", serviceCode)
                    .addValue("capabilityCode", capabilityCode)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now));
        }
    }

    private List<String> serviceCodes(String tenant, String capabilityCode) {
        return jdbc.queryForList("""
                select service_code from service_code_capability_mappings
                where tenant_id=:tenantId and capability_code=:capabilityCode and status='ACTIVE'
                order by service_code asc
                """, new MapSqlParameterSource().addValue("tenantId", tenant).addValue("capabilityCode", capabilityCode), String.class);
    }

    private Optional<ExistingRevision> findRevision(String tenant, String code) {
        try {
            ExistingRevision revision = jdbc.queryForObject("""
                    select capability_id,version,created_at from capability_definitions
                    where tenant_id=:tenantId and capability_code=:capabilityCode
                    """, new MapSqlParameterSource().addValue("tenantId", tenant).addValue("capabilityCode", code),
                    (rs, rowNum) -> new ExistingRevision(rs.getString("capability_id"), rs.getInt("version"), rs.getObject("created_at", OffsetDateTime.class)));
            return Optional.ofNullable(revision);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private void bindDatabaseTenantContext(String tenantId) {
        String tenant = requireTenant(tenantId);
        IamTenantExecutionContext requestContext = IamTenantContextHolder.current().orElse(null);
        if (requestContext != null && !"INSTANCE".equalsIgnoreCase(requestContext.tenantId()) && !tenant.equals(requestContext.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for Capability persistence");
        }
        String actor = requestContext == null || blank(requestContext.actorId()) ? "canonical-capability-management" : requestContext.actorId();
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }

    private String normalizeCapabilityCode(String value) {
        String code = requireNonBlank(value, "capabilityCode").trim().toLowerCase(Locale.ROOT);
        if (!CAPABILITY_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("capabilityCode must use system-neutral lower-case semantic dot notation, for example inventory.availability.read");
        }
        return code;
    }

    private String normalizeStatus(String value) {
        String status = blank(value) ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Unsupported Capability status: " + status);
        return status;
    }

    private String normalizeType(String value) {
        return blank(value) ? "SERVICE" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String normalizeTaxonomy(String value) {
        return blank(value) ? null : value.trim();
    }

    private List<String> normalizeUpperList(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.stream().filter(v -> !blank(v)).map(v -> v.trim().toUpperCase(Locale.ROOT).replace(' ', '_')).forEach(result::add);
        return List.copyOf(result);
    }

    private List<String> normalizeServiceCodes(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.stream().filter(v -> !blank(v)).map(this::normalizeServiceCode).forEach(result::add);
        return List.copyOf(result);
    }

    private String normalizeServiceCode(String value) {
        return requireNonBlank(value, "serviceCode").trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]", "_");
    }

    private String requireTenant(String value) { return requireNonBlank(value, "tenantId"); }
    private int normalizeLimit(int limit) { return Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500)); }
    private String requireNonBlank(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private String firstNonBlank(String first, String second) { return !blank(first) ? first : second; }
    private String trimToNull(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private Map<String, Object> safeMap(Map<String, Object> value) { return value == null ? Map.of() : value; }
    private String firstOrNull(List<String> values) { return values == null || values.isEmpty() ? null : values.getFirst(); }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("Capability JSON field cannot be serialized", ex); }
    }

    private List<String> readStringList(String value) {
        try { return blank(value) ? List.of() : json.readValue(value, STRING_LIST); }
        catch (Exception ex) { throw new IllegalStateException("Capability list JSON cannot be read", ex); }
    }

    private Map<String, Object> readMap(String value) {
        try { return blank(value) ? Map.of() : json.readValue(value, OBJECT_MAP); }
        catch (Exception ex) { throw new IllegalStateException("Capability schema JSON cannot be read", ex); }
    }

    private final class CapabilityRowMapper implements RowMapper<CapabilityDefinition> {
        @Override
        public CapabilityDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CapabilityDefinition(
                    rs.getString("tenant_id"), rs.getString("capability_id"), rs.getString("capability_code"),
                    rs.getString("display_name"), rs.getString("description"), rs.getString("semantic_domain"),
                    rs.getString("category"), rs.getString("capability_type"), readStringList(rs.getString("operations_json")),
                    readMap(rs.getString("input_schema_json")), readMap(rs.getString("output_schema_json")),
                    readStringList(rs.getString("resource_types_json")), readStringList(rs.getString("data_classes_json")),
                    rs.getInt("version"), rs.getString("status"), List.of(),
                    rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
        }
    }

    private record ExistingRevision(String capabilityId, int version, OffsetDateTime createdAt) {}
}
