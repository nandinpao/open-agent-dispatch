package com.opensocket.aievent.core.source;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceSystemManagementService {
    private static final RowMapper<SourceSystemView> ROW_MAPPER = new SourceSystemRowMapper();
    private static final String SELECT_COLUMNS = "tenant_id, source_system_id, display_name, description, status, owner_department_id, owner_group_id, created_at, updated_at";
    private final NamedParameterJdbcTemplate jdbc;

    public SourceSystemManagementService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<SourceSystemView> list(String tenantId) { return list(tenantId, null); }

    /** SQL-level P2.3B filtering; the scope plan is generated server-side and is never accepted from HTTP. */
    @Transactional(readOnly = true)
    public List<SourceSystemView> list(String tenantId, ResourceListScopeQueryPlan scope) {
        String normalizedTenant = requireTenant(tenantId);
        bindDatabaseTenantContext(normalizedTenant);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", normalizedTenant);
        String scoped = "";
        if (scope != null) {
            if (!normalizedTenant.equals(scope.tenantId())) throw new IllegalArgumentException("Tenant scope mismatch");
            scoped = " and " + ScopedResourceSql.predicate(scope, "s", "source_system_id", "owner_department_id", "owner_group_id");
            ScopedResourceSql.bind(params, scope);
        }
        return jdbc.query("select " + SELECT_COLUMNS + " from source_systems s where tenant_id=:tenantId and status<>'RETIRED'" + scoped + " order by source_system_id asc", params, ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public Optional<SourceSystemView> find(String tenantId, String sourceSystemId) {
        String normalizedTenant = requireTenant(tenantId);
        bindDatabaseTenantContext(normalizedTenant);
        String normalizedSource = normalizeSourceSystemId(sourceSystemId);
        try {
            return Optional.ofNullable(jdbc.queryForObject("select " + SELECT_COLUMNS + " from source_systems where tenant_id=:tenantId and source_system_id=:sourceSystemId",
                    new MapSqlParameterSource().addValue("tenantId", normalizedTenant).addValue("sourceSystemId", normalizedSource), ROW_MAPPER));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    @Transactional
    public SourceSystemView create(String tenantId, SourceSystemView request) {
        String normalizedTenant = requireTenant(tenantId);
        bindDatabaseTenantContext(normalizedTenant);
        SourceSystemView body = requireRequest(request);
        String sourceSystemId = normalizeSourceSystemId(body.getSourceSystemId());
        String displayName = requireNonBlank(body.getDisplayName(), "displayName");
        String status = normalizeStatus(body.getStatus(), "ACTIVE");
        String department = normalizeOwner(body.getOwnerDepartmentId());
        String group = normalizeOwner(body.getOwnerGroupId());
        validateOwnerReferences(normalizedTenant, department, group);
        OffsetDateTime now = OffsetDateTime.now();
        int inserted = jdbc.update("""
                insert into source_systems (tenant_id,source_system_id,display_name,description,status,owner_department_id,owner_group_id,created_at,updated_at)
                values (:tenantId,:sourceSystemId,:displayName,:description,:status,:ownerDepartmentId,:ownerGroupId,:createdAt,:updatedAt)
                on conflict (tenant_id,source_system_id) do nothing
                """, new MapSqlParameterSource().addValue("tenantId", normalizedTenant).addValue("sourceSystemId", sourceSystemId)
                .addValue("displayName", displayName).addValue("description", trimToNull(body.getDescription())).addValue("status", status)
                .addValue("ownerDepartmentId", department).addValue("ownerGroupId", group).addValue("createdAt", now).addValue("updatedAt", now));
        if (inserted == 0) {
            throw new com.opensocket.aievent.core.api.StandardApiException(
                    com.opensocket.aievent.core.api.StandardApiErrorCode.CONFLICT,
                    "Source System id already exists. Open the existing Source System and update it instead: " + sourceSystemId);
        }
        return find(normalizedTenant, sourceSystemId).orElseThrow();
    }

    @Transactional
    public SourceSystemView update(String tenantId, String sourceSystemId, SourceSystemView request) {
        String normalizedTenant = requireTenant(tenantId);
        bindDatabaseTenantContext(normalizedTenant);
        String normalizedSource = normalizeSourceSystemId(sourceSystemId);
        SourceSystemView body = requireRequest(request);
        String displayName = requireNonBlank(body.getDisplayName(), "displayName");
        String status = normalizeStatus(body.getStatus(), "ACTIVE");
        String department = normalizeOwner(body.getOwnerDepartmentId());
        String group = normalizeOwner(body.getOwnerGroupId());
        validateOwnerReferences(normalizedTenant, department, group);
        int updated = jdbc.update("""
                update source_systems set display_name=:displayName,description=:description,status=:status,
                  owner_department_id=:ownerDepartmentId,owner_group_id=:ownerGroupId,updated_at=:updatedAt
                where tenant_id=:tenantId and source_system_id=:sourceSystemId
                """, new MapSqlParameterSource().addValue("tenantId", normalizedTenant).addValue("sourceSystemId", normalizedSource)
                .addValue("displayName", displayName).addValue("description", trimToNull(body.getDescription())).addValue("status", status)
                .addValue("ownerDepartmentId", department).addValue("ownerGroupId", group).addValue("updatedAt", OffsetDateTime.now()));
        if (updated == 0) throw new IllegalArgumentException("Source System not found: " + normalizedSource);
        // Child dispatch configuration inherits the Source System governance owner unless explicitly migrated later.
        jdbc.update("update dispatch_flows set owner_department_id=:departmentId,owner_group_id=:groupId where tenant_id=:tenantId and source_system=:sourceSystemId",
                new MapSqlParameterSource().addValue("tenantId", normalizedTenant).addValue("sourceSystemId", normalizedSource).addValue("departmentId", department).addValue("groupId", group));
        jdbc.update("update agent_pools set owner_department_id=:departmentId,owner_group_id=:groupId where tenant_id=:tenantId and source_system=:sourceSystemId",
                new MapSqlParameterSource().addValue("tenantId", normalizedTenant).addValue("sourceSystemId", normalizedSource).addValue("departmentId", department).addValue("groupId", group));
        return find(normalizedTenant, normalizedSource).orElseThrow();
    }

    @Transactional(readOnly = true)
    public void requireAssignableOwner(ResourceListScopeQueryPlan plan, String tenantId, SourceSystemView request) {
        String normalizedTenant = requireTenant(tenantId);
        bindDatabaseTenantContext(normalizedTenant);
        String id = normalizeSourceSystemId(request.getSourceSystemId());
        String department = normalizeOwner(request.getOwnerDepartmentId());
        String group = normalizeOwner(request.getOwnerGroupId());
        if (!ScopedResourceSql.allowsOwnership(jdbc, plan, normalizedTenant, id, department, group)) {
            throw new IllegalArgumentException("The Source System owner is outside your effective Department / Group scope.");
        }
    }

    @Transactional
    public void retire(String tenantId, String sourceSystemId) {
        String normalizedTenant = requireTenant(tenantId);
        bindDatabaseTenantContext(normalizedTenant);
        jdbc.update("update source_systems set status='RETIRED',updated_at=:updatedAt where tenant_id=:tenantId and source_system_id=:sourceSystemId",
                new MapSqlParameterSource().addValue("tenantId", normalizedTenant).addValue("sourceSystemId", normalizeSourceSystemId(sourceSystemId)).addValue("updatedAt", OffsetDateTime.now()));
    }

    /**
     * NamedParameterJdbcTemplate does not pass through TenantTransactionMybatisInterceptor.
     * Establish PostgreSQL's transaction-local tenant/actor variables explicitly on the
     * Spring-bound JDBC connection before touching tenant-RLS protected tables.
     */
    private void bindDatabaseTenantContext(String tenantId) {
        String normalizedTenant = requireTenant(tenantId);
        IamTenantExecutionContext requestContext = IamTenantContextHolder.current().orElse(null);
        if (requestContext != null
                && !"INSTANCE".equalsIgnoreCase(requestContext.tenantId())
                && !normalizedTenant.equals(requestContext.tenantId())) {
            throw new IllegalArgumentException("Tenant context mismatch for Source System persistence");
        }
        String actorId = requestContext == null || requestContext.actorId() == null || requestContext.actorId().isBlank()
                ? "source-system-management"
                : requestContext.actorId();
        jdbc.getJdbcTemplate().queryForObject(
                "select set_config('app.current_tenant_id', ?, true)", String.class, normalizedTenant);
        jdbc.getJdbcTemplate().queryForObject(
                "select set_config('app.current_actor_id', ?, true)", String.class, actorId);
    }

    private void validateOwnerReferences(String tenant, String department, String group) {
        if (department != null) {
            Integer count = jdbc.queryForObject("select count(*) from departments where tenant_id=:tenantId and department_id=:id and status<>'DELETED'",
                    new MapSqlParameterSource().addValue("tenantId",tenant).addValue("id",department), Integer.class);
            if (count == null || count == 0) throw new IllegalArgumentException("ownerDepartmentId is not an active Department in this Tenant");
        }
        if (group != null) {
            Integer count = jdbc.queryForObject("select count(*) from organization_groups where tenant_id=:tenantId and group_id=:id and status<>'DELETED'",
                    new MapSqlParameterSource().addValue("tenantId",tenant).addValue("id",group), Integer.class);
            if (count == null || count == 0) throw new IllegalArgumentException("ownerGroupId is not an active Group in this Tenant");
        }
    }

    private SourceSystemView requireRequest(SourceSystemView request) { if (request == null) throw new IllegalArgumentException("Source System request body is required"); return request; }
    private String requireTenant(String tenantId) { if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required"); return tenantId.trim(); }
    private String normalizeSourceSystemId(String sourceSystemId) {
        String value=requireNonBlank(sourceSystemId,"sourceSystemId").trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]","_").replaceAll("^_+|_+$","");
        if(value.isBlank())throw new IllegalArgumentException("sourceSystemId is required");return value;
    }
    private String normalizeStatus(String value,String fallback){String status=value==null||value.isBlank()?fallback:value.trim().toUpperCase(Locale.ROOT);if(!List.of("ACTIVE","DISABLED","RETIRED").contains(status))throw new IllegalArgumentException("Unsupported Source System status: "+status);return status;}
    private String normalizeOwner(String value){return value==null||value.isBlank()?null:value.trim();}
    private String requireNonBlank(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private String trimToNull(String value){return value==null||value.isBlank()?null:value.trim();}

    private static final class SourceSystemRowMapper implements RowMapper<SourceSystemView> {
        @Override public SourceSystemView mapRow(ResultSet rs,int rowNum)throws SQLException{
            SourceSystemView v=new SourceSystemView();v.setTenantId(rs.getString("tenant_id"));v.setSourceSystemId(rs.getString("source_system_id"));v.setDisplayName(rs.getString("display_name"));v.setDescription(rs.getString("description"));v.setStatus(rs.getString("status"));v.setOwnerDepartmentId(rs.getString("owner_department_id"));v.setOwnerGroupId(rs.getString("owner_group_id"));v.setCreatedAt(rs.getObject("created_at",OffsetDateTime.class));v.setUpdatedAt(rs.getObject("updated_at",OffsetDateTime.class));return v;
        }
    }
}
