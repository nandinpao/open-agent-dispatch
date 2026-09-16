package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * RS4 SQL-first Dispatch Request query. Dispatch Request has no independent ACL: its visibility is
 * exactly the Resource Scope of the linked Task.
 */
@Component
@ConditionalOnProperty(prefix="resource-access", name={"enabled","business-data-enabled"}, havingValue="true")
public final class ScopedDispatchRequestQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ExecutionOperationalQuery query;

    public ScopedDispatchRequestQueryService(NamedParameterJdbcTemplate jdbc, ExecutionOperationalQuery query) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.query = Objects.requireNonNull(query);
    }

    public List<DispatchRequest> recent(ResourceListScopeQueryPlan plan, DispatchRequestStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        MapSqlParameterSource params = params(plan).addValue("status", status == null ? null : status.name()).addValue("limit", safeLimit);
        String sql = "select d.dispatch_request_id from dispatch_requests d join tasks t on t.tenant_id=d.tenant_id and t.task_id=d.task_id "
                + "where d.tenant_id=:tenantId and (:status is null or d.status=:status) and " + taskPredicate(plan)
                + " order by d.updated_at desc,d.dispatch_request_id desc limit :limit";
        return load(jdbc.query(sql, params, (rs,rowNum)->rs.getString(1)), plan.tenantId());
    }

    private List<DispatchRequest> load(List<String> ids, String tenantId) {
        List<DispatchRequest> out = new ArrayList<>();
        for (String id : ids) {
            query.findDispatchRequest(id).filter(value -> tenantId.equals(value.getTenantId())).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    private static MapSqlParameterSource params(ResourceListScopeQueryPlan plan) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", plan.tenantId());
        ScopedResourceSql.bind(p, plan);
        return p;
    }

    /** Same positive/deny semantics as TaskDao.searchAuthorized, applied before LIMIT. */
    private static String taskPredicate(ResourceListScopeQueryPlan plan) {
        if (plan == null || plan.denyAll()) return "1=0";
        List<String> positive = new ArrayList<>();
        if (plan.tenantWide()) positive.add("1=1");
        if (!plan.explicitResourceIds().isEmpty()) positive.add("t.task_id in (:scopeResources)");
        if (!plan.exactDepartmentIds().isEmpty()) positive.add("(t.owner_department_id in (:scopeDepartments) or t.requester_department_id in (:scopeDepartments) or t.executor_department_id in (:scopeDepartments))");
        if (!plan.subtreeDepartmentRootIds().isEmpty()) positive.add("exists (select 1 from org_department_closure dc where dc.tenant_id=t.tenant_id and dc.ancestor_department_id in (:scopeSubtreeRoots) and dc.descendant_department_id in (t.owner_department_id,t.requester_department_id,t.executor_department_id))");
        if (!plan.groupIds().isEmpty()) positive.add("(t.owner_group_id in (:scopeGroups) or t.requester_group_id in (:scopeGroups) or t.executor_group_id in (:scopeGroups))");
        if (positive.isEmpty()) return "1=0";
        List<String> predicates = new ArrayList<>();
        predicates.add("(" + String.join(" or ", positive) + ")");
        if (!plan.excludedResourceIds().isEmpty()) predicates.add("t.task_id not in (:scopeExcludedResources)");
        if (!plan.deniedDepartmentIds().isEmpty()) predicates.add("coalesce(t.owner_department_id,'') not in (:scopeDeniedDepartments) and coalesce(t.requester_department_id,'') not in (:scopeDeniedDepartments) and coalesce(t.executor_department_id,'') not in (:scopeDeniedDepartments)");
        if (!plan.deniedSubtreeDepartmentRootIds().isEmpty()) predicates.add("not exists (select 1 from org_department_closure dd where dd.tenant_id=t.tenant_id and dd.ancestor_department_id in (:scopeDeniedSubtreeRoots) and dd.descendant_department_id in (t.owner_department_id,t.requester_department_id,t.executor_department_id))");
        if (!plan.deniedGroupIds().isEmpty()) predicates.add("coalesce(t.owner_group_id,'') not in (:scopeDeniedGroups) and coalesce(t.requester_group_id,'') not in (:scopeDeniedGroups) and coalesce(t.executor_group_id,'') not in (:scopeDeniedGroups)");
        return String.join(" and ", predicates);
    }
}
