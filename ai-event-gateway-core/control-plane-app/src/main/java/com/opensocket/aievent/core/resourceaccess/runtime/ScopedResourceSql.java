package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** P2.3B trusted SQL predicate compiler for business tables with Department/Group ownership columns. */
public final class ScopedResourceSql {
    private ScopedResourceSql() { }

    public static String predicate(ResourceListScopeQueryPlan plan, String alias,
            String resourceIdColumn, String departmentColumn, String groupColumn) {
        if (plan == null || plan.denyAll()) return "1=0";
        String resource = alias + "." + resourceIdColumn;
        String department = alias + "." + departmentColumn;
        String group = alias + "." + groupColumn;
        List<String> positive = new ArrayList<>();
        if (plan.tenantWide()) positive.add("1=1");
        if (!plan.exactDepartmentIds().isEmpty()) positive.add(department + " in (:scopeDepartments)");
        if (!plan.subtreeDepartmentRootIds().isEmpty()) positive.add("exists (select 1 from org_department_closure scope_dc where scope_dc.tenant_id=" + alias + ".tenant_id and scope_dc.ancestor_department_id in (:scopeSubtreeRoots) and scope_dc.descendant_department_id=" + department + ")");
        if (!plan.groupIds().isEmpty()) positive.add(group + " in (:scopeGroups)");
        if (!plan.explicitResourceIds().isEmpty()) positive.add(resource + " in (:scopeResources)");
        if (positive.isEmpty()) return "1=0";

        List<String> all = new ArrayList<>();
        all.add("(" + String.join(" or ", positive) + ")");
        if (!plan.excludedResourceIds().isEmpty()) all.add(resource + " not in (:scopeExcludedResources)");
        if (!plan.deniedDepartmentIds().isEmpty()) all.add("(" + department + " is null or " + department + " not in (:scopeDeniedDepartments))");
        if (!plan.deniedSubtreeDepartmentRootIds().isEmpty()) all.add("(" + department + " is null or not exists (select 1 from org_department_closure deny_dc where deny_dc.tenant_id=" + alias + ".tenant_id and deny_dc.ancestor_department_id in (:scopeDeniedSubtreeRoots) and deny_dc.descendant_department_id=" + department + "))");
        if (!plan.deniedGroupIds().isEmpty()) all.add("(" + group + " is null or " + group + " not in (:scopeDeniedGroups))");
        return String.join(" and ", all);
    }

    /** Checks a proposed resource owner against the trusted plan before a create/re-scope mutation. */
    public static boolean allowsOwnership(NamedParameterJdbcTemplate jdbc, ResourceListScopeQueryPlan plan,
            String tenantId, String resourceId, String ownerDepartmentId, String ownerGroupId) {
        if (plan == null || plan.denyAll()) return false;
        if (plan.excludedResourceIds().contains(resourceId)) return false;
        if (ownerDepartmentId != null && plan.deniedDepartmentIds().contains(ownerDepartmentId)) return false;
        if (ownerGroupId != null && plan.deniedGroupIds().contains(ownerGroupId)) return false;
        if (ownerDepartmentId != null && !plan.deniedSubtreeDepartmentRootIds().isEmpty()) {
            Integer denied = jdbc.queryForObject("select count(*) from org_department_closure where tenant_id=:tenantId and ancestor_department_id in (:roots) and descendant_department_id=:departmentId",
                    new MapSqlParameterSource().addValue("tenantId",tenantId).addValue("roots",plan.deniedSubtreeDepartmentRootIds()).addValue("departmentId",ownerDepartmentId), Integer.class);
            if (denied != null && denied > 0) return false;
        }
        if (plan.tenantWide() || plan.explicitResourceIds().contains(resourceId)) return true;
        if (ownerDepartmentId != null && plan.exactDepartmentIds().contains(ownerDepartmentId)) return true;
        if (ownerGroupId != null && plan.groupIds().contains(ownerGroupId)) return true;
        if (ownerDepartmentId != null && !plan.subtreeDepartmentRootIds().isEmpty()) {
            Integer allowed = jdbc.queryForObject("select count(*) from org_department_closure where tenant_id=:tenantId and ancestor_department_id in (:roots) and descendant_department_id=:departmentId",
                    new MapSqlParameterSource().addValue("tenantId",tenantId).addValue("roots",plan.subtreeDepartmentRootIds()).addValue("departmentId",ownerDepartmentId), Integer.class);
            return allowed != null && allowed > 0;
        }
        return false;
    }

    public static void bind(MapSqlParameterSource params, ResourceListScopeQueryPlan plan) {
        if (plan == null) return;
        if (!plan.exactDepartmentIds().isEmpty()) params.addValue("scopeDepartments", plan.exactDepartmentIds());
        if (!plan.subtreeDepartmentRootIds().isEmpty()) params.addValue("scopeSubtreeRoots", plan.subtreeDepartmentRootIds());
        if (!plan.groupIds().isEmpty()) params.addValue("scopeGroups", plan.groupIds());
        if (!plan.explicitResourceIds().isEmpty()) params.addValue("scopeResources", plan.explicitResourceIds());
        if (!plan.excludedResourceIds().isEmpty()) params.addValue("scopeExcludedResources", plan.excludedResourceIds());
        if (!plan.deniedDepartmentIds().isEmpty()) params.addValue("scopeDeniedDepartments", plan.deniedDepartmentIds());
        if (!plan.deniedSubtreeDepartmentRootIds().isEmpty()) params.addValue("scopeDeniedSubtreeRoots", plan.deniedSubtreeDepartmentRootIds());
        if (!plan.deniedGroupIds().isEmpty()) params.addValue("scopeDeniedGroups", plan.deniedGroupIds());
    }
}
