package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * RS5 SQL-first canonical Issue query. The Issue is represented by the Task-Issue Link read model,
 * but visibility comes from its immutable Task-origin scope snapshot plus current governed
 * requester/executor collaboration scope. No external provider ACL participates in this query.
 */
@Component
@ConditionalOnProperty(prefix="resource-access", name={"enabled","business-data-enabled"}, havingValue="true")
public final class ScopedIssueQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TaskIssueLinkRepository links;

    public ScopedIssueQueryService(NamedParameterJdbcTemplate jdbc, TaskIssueLinkRepository links) {
        this.jdbc=Objects.requireNonNull(jdbc); this.links=Objects.requireNonNull(links);
    }

    public List<TaskIssueLink> recent(ResourceListScopeQueryPlan plan, int limit) {
        return query(plan,null,limit);
    }

    public List<TaskIssueLink> byTask(ResourceListScopeQueryPlan plan,String taskId,int limit) {
        if(taskId==null||taskId.isBlank())return List.of();
        return query(plan,taskId.trim(),limit);
    }

    private List<TaskIssueLink> query(ResourceListScopeQueryPlan plan,String taskId,int limit){
        int safe=Math.max(1,Math.min(limit,500));
        MapSqlParameterSource params=params(plan).addValue("taskId",taskId).addValue("limit",safe);
        String sql="select l.link_id from task_issue_links l where l.tenant_id=:tenantId "
                +"and l.scope_status='RESOLVED' and (:taskId is null or l.task_id=:taskId) and " + predicate(plan)
                +" order by l.updated_at desc,l.link_id desc limit :limit";
        List<String> ids=jdbc.query(sql,params,(rs,rowNum)->rs.getString(1));
        List<TaskIssueLink> out=new ArrayList<>();
        for(String id:ids) links.findByTenantAndLinkId(plan.tenantId(),id).ifPresent(out::add);
        return List.copyOf(out);
    }

    private static MapSqlParameterSource params(ResourceListScopeQueryPlan plan){
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("tenantId",plan==null?"":plan.tenantId());
        ScopedResourceSql.bind(p,plan); return p;
    }

    private static String predicate(ResourceListScopeQueryPlan plan){
        if(plan==null||plan.denyAll())return "1=0";
        List<String> positive=new ArrayList<>();
        if(plan.tenantWide())positive.add("1=1");
        if(!plan.explicitResourceIds().isEmpty())positive.add("l.link_id in (:scopeResources)");
        if(!plan.exactDepartmentIds().isEmpty())positive.add("(l.owner_department_id in (:scopeDepartments) or l.requester_department_id in (:scopeDepartments) or l.executor_department_id in (:scopeDepartments))");
        if(!plan.subtreeDepartmentRootIds().isEmpty())positive.add("exists (select 1 from org_department_closure dc where dc.tenant_id=l.tenant_id and dc.ancestor_department_id in (:scopeSubtreeRoots) and dc.descendant_department_id in (l.owner_department_id,l.requester_department_id,l.executor_department_id))");
        if(!plan.groupIds().isEmpty())positive.add("(l.owner_group_id in (:scopeGroups) or l.requester_group_id in (:scopeGroups) or l.executor_group_id in (:scopeGroups))");
        if(positive.isEmpty())return "1=0";
        List<String> all=new ArrayList<>(); all.add("("+String.join(" or ",positive)+")");
        if(!plan.excludedResourceIds().isEmpty())all.add("l.link_id not in (:scopeExcludedResources)");
        if(!plan.deniedDepartmentIds().isEmpty())all.add("coalesce(l.owner_department_id,'') not in (:scopeDeniedDepartments) and coalesce(l.requester_department_id,'') not in (:scopeDeniedDepartments) and coalesce(l.executor_department_id,'') not in (:scopeDeniedDepartments)");
        if(!plan.deniedSubtreeDepartmentRootIds().isEmpty())all.add("not exists (select 1 from org_department_closure dd where dd.tenant_id=l.tenant_id and dd.ancestor_department_id in (:scopeDeniedSubtreeRoots) and dd.descendant_department_id in (l.owner_department_id,l.requester_department_id,l.executor_department_id))");
        if(!plan.deniedGroupIds().isEmpty())all.add("coalesce(l.owner_group_id,'') not in (:scopeDeniedGroups) and coalesce(l.requester_group_id,'') not in (:scopeDeniedGroups) and coalesce(l.executor_group_id,'') not in (:scopeDeniedGroups)");
        return String.join(" and ",all);
    }
}
