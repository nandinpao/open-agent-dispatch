package com.opensocket.aievent.core.dispatch.flow;

import static com.opensocket.aievent.core.dispatch.flow.DispatchFlowNormalizationSupport.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;

/** Server-authoritative Source System ownership resolver shared by Flow and Agent Pool aggregates. */
final class DispatchSourceOwnershipResolver {
    private final NamedParameterJdbcTemplate jdbc;

    DispatchSourceOwnershipResolver(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void requireAssignable(ResourceListScopeQueryPlan plan, String tenantId, String resourceId, String sourceSystem) {
        String tenant = normalizeTenant(tenantId);
        String source = normalizeCode(requireNonBlank(sourceSystem, "sourceSystem"));
        Map<String, Object> owner = resolve(tenant, source);
        if (!ScopedResourceSql.allowsOwnership(jdbc, plan, tenant, requireNonBlank(resourceId, "resourceId"),
                (String) owner.get("ownerDepartmentId"), (String) owner.get("ownerGroupId"))) {
            throw new IllegalArgumentException("The Dispatch resource is outside your effective Department / Group scope.");
        }
    }

    void applyToAgentPool(String tenantId, String sourceSystem, AgentPoolView target) {
        if (blank(sourceSystem)) {
            target.setOwnerDepartmentId(null);
            target.setOwnerGroupId(null);
            return;
        }
        Map<String, Object> row = resolve(tenantId, sourceSystem);
        target.setOwnerDepartmentId((String) row.get("ownerDepartmentId"));
        target.setOwnerGroupId((String) row.get("ownerGroupId"));
    }

    void applyToFlow(String tenantId, String sourceSystem, DispatchFlowView target) {
        if (blank(sourceSystem)) {
            target.setOwnerDepartmentId(null);
            target.setOwnerGroupId(null);
            return;
        }
        Map<String, Object> row = resolve(tenantId, sourceSystem);
        target.setOwnerDepartmentId((String) row.get("ownerDepartmentId"));
        target.setOwnerGroupId((String) row.get("ownerGroupId"));
    }

    private Map<String, Object> resolve(String tenantId, String sourceSystem) {
        try {
            return jdbc.queryForObject(
                    "select owner_department_id,owner_group_id from source_systems where tenant_id=:tenantId and source_system_id=:sourceSystem",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("sourceSystem", sourceSystem),
                    (rs, n) -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("ownerDepartmentId", rs.getString("owner_department_id"));
                        result.put("ownerGroupId", rs.getString("owner_group_id"));
                        return result;
                    });
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("Source System not found in selected tenant: " + sourceSystem);
        }
    }
}
