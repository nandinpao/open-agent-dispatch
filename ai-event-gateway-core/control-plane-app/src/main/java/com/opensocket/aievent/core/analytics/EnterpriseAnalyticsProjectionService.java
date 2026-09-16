package com.opensocket.aievent.core.analytics;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 12.6 read-only enterprise analytics projection facade.
 * The analytics_* tables are rebuildable projections and must never be used as authorization authority.
 */
@Service
@Transactional(readOnly=true)
public class EnterpriseAnalyticsProjectionService {
    private final NamedParameterJdbcTemplate jdbc;

    public EnterpriseAnalyticsProjectionService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ProjectionStatus projectionStatus(String tenantId) {
        tenant(tenantId);
        var p = new MapSqlParameterSource("tenantId", tenantId);
        return jdbc.query("""
                select c.projection_name,c.last_event_id,c.last_event_at,c.last_projected_at,c.projected_event_count,c.failure_count,c.rebuild_required,c.updated_at,
                       (select count(*) from enterprise_analytics_projection_events e where e.tenant_id=c.tenant_id and e.projection_status='PENDING') pending_events,
                       (select count(*) from enterprise_analytics_projection_events e where e.tenant_id=c.tenant_id and e.projection_status='FAILED') failed_events,
                       case when c.last_event_at is null then null else greatest(0,extract(epoch from(now()-c.last_event_at))) end lag_seconds
                  from enterprise_analytics_projection_checkpoints c
                 where c.tenant_id=:tenantId and c.projection_name='ENTERPRISE_ANALYTICS'
                """, p, (rs, n) -> new ProjectionStatus(
                        rs.getString("projection_name"), rs.getString("last_event_id"),
                        rs.getObject("last_event_at", OffsetDateTime.class), rs.getObject("last_projected_at", OffsetDateTime.class),
                        rs.getLong("projected_event_count"), rs.getLong("failure_count"), rs.getBoolean("rebuild_required"),
                        rs.getLong("pending_events"),rs.getLong("failed_events"),number(rs.getObject("lag_seconds")),rs.getObject("updated_at", OffsetDateTime.class)))
                .stream().findFirst().orElse(new ProjectionStatus("ENTERPRISE_ANALYTICS", null, null, null, 0, 0, false, 0, 0, null, null));
    }

    public WorkloadSummary workloadSummary(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId) {
        tenant(tenantId);
        QueryScope q = scope(tenantId, from, to, departmentId, groupId);
        return jdbc.queryForObject("""
                select count(*) total_tasks,
                       count(*) filter(where status in('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED','BLOCKED','RETRY_WAIT')) failed_tasks,
                       count(*) filter(where initial_priority in('P0','P1','CRITICAL','URGENT') or initial_severity='CRITICAL') critical_tasks,
                       count(*) filter(where origin_principal_type='USER') human_origin_tasks,
                       count(*) filter(where origin_principal_type='SERVICE_ACCOUNT') service_account_origin_tasks,
                       count(*) filter(where origin_principal_type='AGENT') agent_origin_tasks,
                       count(*) filter(where origin_principal_type='SYSTEM') system_origin_tasks,
                       count(distinct assigned_agent_id) filter(where assigned_agent_id is not null) distinct_agents,
                       count(distinct credential_id) filter(where credential_id is not null) distinct_credentials,
                       avg(duration_ms) filter(where duration_ms is not null) avg_duration_ms
                  from analytics_workload_fact
                 where tenant_id=:tenantId
                   and occurred_at>=:fromAt and occurred_at<:toAt
                   and (:departmentId is null or origin_department_id=:departmentId)
                   and (:groupId is null or origin_group_id=:groupId)
                """, q.params(), (rs, n) -> new WorkloadSummary(
                        rs.getLong("total_tasks"), rs.getLong("failed_tasks"), rs.getLong("critical_tasks"),
                        rs.getLong("human_origin_tasks"), rs.getLong("service_account_origin_tasks"), rs.getLong("agent_origin_tasks"), rs.getLong("system_origin_tasks"),
                        rs.getLong("distinct_agents"), rs.getLong("distinct_credentials"),
                        rs.getObject("avg_duration_ms") == null ? null : rs.getDouble("avg_duration_ms")));
    }

    public List<BreakdownRow> byOrigin(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId, int limit) {
        tenant(tenantId);
        QueryScope q = scope(tenantId, from, to, departmentId, groupId).withLimit(limit);
        return jdbc.query("""
                select coalesce(origin_principal_type,'UNKNOWN') dimension_key,
                       count(*) task_count,
                       count(*) filter(where failure_domain<>'NONE') failure_count,
                       count(*) filter(where initial_priority in('P0','P1','CRITICAL','URGENT') or initial_severity='CRITICAL') critical_count
                  from analytics_workload_fact
                 where tenant_id=:tenantId and occurred_at>=:fromAt and occurred_at<:toAt
                   and (:departmentId is null or origin_department_id=:departmentId)
                   and (:groupId is null or origin_group_id=:groupId)
                 group by coalesce(origin_principal_type,'UNKNOWN')
                 order by task_count desc,dimension_key limit :limit
                """, q.params(), BREAKDOWN_MAPPER);
    }

    public List<BreakdownRow> byFailureDomain(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId, int limit) {
        tenant(tenantId);
        QueryScope q = scope(tenantId, from, to, departmentId, groupId).withLimit(limit);
        return jdbc.query("""
                select coalesce(failure_domain,'NONE') dimension_key,count(*) task_count,
                       count(*) filter(where failure_domain<>'NONE') failure_count,
                       count(*) filter(where initial_priority in('P0','P1','CRITICAL','URGENT') or initial_severity='CRITICAL') critical_count
                  from analytics_workload_fact
                 where tenant_id=:tenantId and occurred_at>=:fromAt and occurred_at<:toAt
                   and (:departmentId is null or origin_department_id=:departmentId)
                   and (:groupId is null or origin_group_id=:groupId)
                 group by coalesce(failure_domain,'NONE') order by task_count desc,dimension_key limit :limit
                """, q.params(), BREAKDOWN_MAPPER);
    }

    public List<OrganizationRow> byDepartment(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit) {
        tenant(tenantId);
        var p = base(tenantId, from, to).addValue("limit", bounded(limit));
        return jdbc.query("""
                select coalesce(f.origin_department_id,'UNASSIGNED') department_id,
                       coalesce(d.department_name,'Unassigned / historical') department_name,
                       count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,
                       count(distinct f.assigned_agent_id) filter(where f.assigned_agent_id is not null) agent_count
                  from analytics_workload_fact f
                  left join analytics_dim_department_history d
                    on d.tenant_id=f.tenant_id and d.department_id=f.origin_department_id and d.dimension_version_id=f.origin_department_version_id
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt
                 group by coalesce(f.origin_department_id,'UNASSIGNED'),coalesce(d.department_name,'Unassigned / historical')
                 order by task_count desc,department_id limit :limit
                """, p, (rs,n)->new OrganizationRow(rs.getString("department_id"),rs.getString("department_name"),rs.getLong("task_count"),rs.getLong("failure_count"),rs.getLong("agent_count")));
    }

    public SecuritySummary securitySummary(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId) {
        tenant(tenantId);
        var p = base(tenantId,from,to).addValue("departmentId", text(departmentId));
        return jdbc.queryForObject("""
                select count(*) incident_count,
                       count(*) filter(where status<>'RESOLVED') open_incident_count,
                       count(*) filter(where severity='CRITICAL') critical_incident_count,
                       coalesce(sum(total_control_action_count),0) control_action_count,
                       avg(containment_latency_ms) filter(where containment_latency_ms is not null) avg_containment_ms,
                       avg(resolution_latency_ms) filter(where resolution_latency_ms is not null) avg_resolution_ms
                  from analytics_security_incident_fact
                 where tenant_id=:tenantId and opened_at>=:fromAt and opened_at<:toAt
                   and (:departmentId is null or owner_department_id=:departmentId)
                """,p,(rs,n)->new SecuritySummary(rs.getLong("incident_count"),rs.getLong("open_incident_count"),rs.getLong("critical_incident_count"),
                        rs.getLong("control_action_count"),number(rs.getObject("avg_containment_ms")),number(rs.getObject("avg_resolution_ms"))));
    }

    public List<Map<String,Object>> dimensionHistory(String tenantId,String type,String id,int limit) {
        tenant(tenantId);
        String table; String idColumn; String nameColumn;
        switch(type == null ? "" : type.trim().toUpperCase()) {
            case "DEPARTMENT" -> { table="analytics_dim_department_history"; idColumn="department_id"; nameColumn="department_name"; }
            case "GROUP" -> { table="analytics_dim_group_history"; idColumn="group_id"; nameColumn="group_name"; }
            default -> throw new IllegalArgumentException("dimensionType must be DEPARTMENT or GROUP");
        }
        var p=new MapSqlParameterSource("tenantId",tenantId).addValue("id",id).addValue("limit",bounded(limit));
        String sql="select "+idColumn+" dimension_id,"+nameColumn+" dimension_name,status,dimension_version_id,valid_from,valid_to,is_current from "+table+
                " where tenant_id=:tenantId and "+idColumn+"=:id order by valid_from desc limit :limit";
        return jdbc.queryForList(sql,p);
    }


    public List<TrendRow> workloadTrend(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId, String bucket) {
        tenant(tenantId);
        QueryScope q = scope(tenantId, from, to, departmentId, groupId);
        String normalized = bucket == null ? "DAY" : bucket.trim().toUpperCase();
        if (!normalized.equals("HOUR") && !normalized.equals("DAY")) throw new IllegalArgumentException("bucket must be HOUR or DAY");
        q.params().addValue("bucket", normalized);
        return jdbc.query("""
                select case when :bucket='HOUR' then bucket_start else date_trunc('day',bucket_start) end bucket_start,
                       sum(task_count) task_count,sum(failed_count) failure_count,sum(critical_count) critical_count,
                       case when sum(duration_sample_count)=0 then null else sum(total_duration_ms)::numeric/sum(duration_sample_count) end avg_duration_ms
                  from analytics_workload_hourly_rollup
                 where tenant_id=:tenantId and bucket_start>=:fromAt and bucket_start<:toAt
                   and (:departmentId is null or department_id=:departmentId)
                   and (:groupId is null or group_id=:groupId)
                 group by case when :bucket='HOUR' then bucket_start else date_trunc('day',bucket_start) end
                 order by bucket_start
                """, q.params(), (rs,n)->new TrendRow(rs.getObject("bucket_start",OffsetDateTime.class),rs.getLong("task_count"),rs.getLong("failure_count"),rs.getLong("critical_count"),number(rs.getObject("avg_duration_ms"))));
    }

    public List<OrganizationRow> byGroup(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, int limit) {
        tenant(tenantId);
        var p = base(tenantId,from,to).addValue("departmentId",text(departmentId)).addValue("limit",bounded(limit));
        return jdbc.query("""
                select coalesce(f.origin_group_id,'UNASSIGNED') department_id,
                       coalesce(g.group_name,'Unassigned / historical') department_name,
                       count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,
                       count(distinct f.assigned_agent_id) filter(where f.assigned_agent_id is not null) agent_count
                  from analytics_workload_fact f
                  left join analytics_dim_group_history g
                    on g.tenant_id=f.tenant_id and g.group_id=f.origin_group_id and g.dimension_version_id=f.origin_group_version_id
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt
                   and (:departmentId is null or f.origin_department_id=:departmentId)
                 group by coalesce(f.origin_group_id,'UNASSIGNED'),coalesce(g.group_name,'Unassigned / historical')
                 order by task_count desc,department_id limit :limit
                """,p,(rs,n)->new OrganizationRow(rs.getString("department_id"),rs.getString("department_name"),rs.getLong("task_count"),rs.getLong("failure_count"),rs.getLong("agent_count")));
    }

    public List<RankedWorkloadRow> byAgent(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId, int limit) {
        tenant(tenantId);
        QueryScope q=scope(tenantId,from,to,departmentId,groupId).withLimit(limit);
        return jdbc.query("""
                select f.assigned_agent_id dimension_key,coalesce(max(p.agent_name),f.assigned_agent_id) dimension_label,
                       count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,
                       count(*) filter(where f.initial_priority in('P0','P1','CRITICAL','URGENT') or f.initial_severity='CRITICAL') critical_count
                  from analytics_workload_fact f
                  left join agent_profiles p on p.tenant_id=f.tenant_id and p.agent_id=f.assigned_agent_id
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt and f.assigned_agent_id is not null
                   and (:departmentId is null or f.origin_department_id=:departmentId)
                   and (:groupId is null or f.origin_group_id=:groupId)
                 group by f.assigned_agent_id order by failure_count desc,task_count desc,dimension_key limit :limit
                """,q.params(),RANKED_MAPPER);
    }

    public List<RankedWorkloadRow> byCredential(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId, int limit) {
        tenant(tenantId);
        QueryScope q=scope(tenantId,from,to,departmentId,groupId).withLimit(limit);
        return jdbc.query("""
                select f.credential_id dimension_key,coalesce(max(c.credential_name),f.credential_id) dimension_label,
                       count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,
                       count(*) filter(where f.initial_priority in('P0','P1','CRITICAL','URGENT') or f.initial_severity='CRITICAL') critical_count
                  from analytics_workload_fact f
                  left join token_service_account_credentials c on c.tenant_id=f.tenant_id and c.credential_id=f.credential_id
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt and f.credential_id is not null
                   and (:departmentId is null or f.origin_department_id=:departmentId)
                   and (:groupId is null or f.origin_group_id=:groupId)
                 group by f.credential_id order by failure_count desc,task_count desc,dimension_key limit :limit
                """,q.params(),RANKED_MAPPER);
    }

    public List<RankedWorkloadRow> bySourceSystem(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId, int limit) {
        tenant(tenantId);
        QueryScope q=scope(tenantId,from,to,departmentId,groupId).withLimit(limit);
        return jdbc.query("""
                select f.source_system dimension_key,coalesce(max(s.display_name),f.source_system) dimension_label,
                       count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,
                       count(*) filter(where f.initial_priority in('P0','P1','CRITICAL','URGENT') or f.initial_severity='CRITICAL') critical_count
                  from analytics_workload_fact f
                  left join source_systems s on s.tenant_id=f.tenant_id and s.source_system_id=f.source_system
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt and f.source_system is not null and f.source_system<>''
                   and (:departmentId is null or f.origin_department_id=:departmentId)
                   and (:groupId is null or f.origin_group_id=:groupId)
                 group by f.source_system order by failure_count desc,task_count desc,dimension_key limit :limit
                """,q.params(),RANKED_MAPPER);
    }

    public List<SecurityOrganizationRow> securityByDepartment(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit) {
        tenant(tenantId);
        var p=base(tenantId,from,to).addValue("limit",bounded(limit));
        return jdbc.query("""
                select coalesce(f.owner_department_id,'UNASSIGNED') department_id,
                       coalesce(d.department_name,'Unassigned / historical') department_name,
                       count(*) incident_count,count(*) filter(where f.status<>'RESOLVED') open_count,
                       count(*) filter(where f.severity='CRITICAL') critical_count,
                       avg(f.containment_latency_ms) filter(where f.containment_latency_ms is not null) avg_containment_ms,
                       avg(f.resolution_latency_ms) filter(where f.resolution_latency_ms is not null) avg_resolution_ms
                  from analytics_security_incident_fact f
                  left join analytics_dim_department_history d on d.tenant_id=f.tenant_id and d.department_id=f.owner_department_id and d.dimension_version_id=f.owner_department_version_id
                 where f.tenant_id=:tenantId and f.opened_at>=:fromAt and f.opened_at<:toAt
                 group by coalesce(f.owner_department_id,'UNASSIGNED'),coalesce(d.department_name,'Unassigned / historical')
                 order by critical_count desc,open_count desc,incident_count desc,department_id limit :limit
                """,p,(rs,n)->new SecurityOrganizationRow(rs.getString("department_id"),rs.getString("department_name"),rs.getLong("incident_count"),rs.getLong("open_count"),rs.getLong("critical_count"),number(rs.getObject("avg_containment_ms")),number(rs.getObject("avg_resolution_ms"))));
    }

    public RecentWorkloadPage recentWorkloads(String tenantId, OffsetDateTime from, OffsetDateTime to, String departmentId, String groupId, int limit, String cursor) {
        tenant(tenantId);
        QueryScope q=scope(tenantId,from,to,departmentId,groupId);
        CursorPoint point=decodeCursor(cursor);
        int boundedLimit=Math.max(1,Math.min(limit,200));
        q.params().addValue("afterAt",point==null?null:point.occurredAt()).addValue("afterId",point==null?null:point.taskId()).addValue("limit",boundedLimit+1);
        var rows=jdbc.query("""
                select task_id,occurred_at,status,origin_principal_type,origin_principal_id,origin_department_id,origin_group_id,
                       assigned_agent_id,credential_id,source_system,failure_domain,failure_code,initial_priority,initial_severity
                  from analytics_workload_fact
                 where tenant_id=:tenantId and occurred_at>=:fromAt and occurred_at<:toAt
                   and (:departmentId is null or origin_department_id=:departmentId)
                   and (:groupId is null or origin_group_id=:groupId)
                   and (:afterAt is null or occurred_at<:afterAt or (occurred_at=:afterAt and task_id<:afterId))
                 order by occurred_at desc,task_id desc limit :limit
                """,q.params(),(rs,n)->new RecentWorkloadRow(rs.getString("task_id"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getString("status"),rs.getString("origin_principal_type"),rs.getString("origin_principal_id"),rs.getString("origin_department_id"),rs.getString("origin_group_id"),rs.getString("assigned_agent_id"),rs.getString("credential_id"),rs.getString("source_system"),rs.getString("failure_domain"),rs.getString("failure_code"),rs.getString("initial_priority"),rs.getString("initial_severity")));
        boolean more=rows.size()>boundedLimit;
        var page=more?rows.subList(0,boundedLimit):rows;
        String next=more&&!page.isEmpty()?encodeCursor(page.get(page.size()-1).occurredAt(),page.get(page.size()-1).taskId()):"";
        return new RecentWorkloadPage(List.copyOf(page),next,more);
    }

    public DashboardPerformanceStatus dashboardPerformanceStatus(String tenantId) {
        tenant(tenantId);
        var p=new MapSqlParameterSource("tenantId",tenantId);
        return jdbc.queryForObject("""
                select (select count(*) from analytics_workload_rollup_dirty where tenant_id=:tenantId) dirty_rollup_buckets,
                       (select min(bucket_start) from analytics_workload_rollup_dirty where tenant_id=:tenantId) oldest_dirty_bucket,
                       (select max(refreshed_at) from analytics_workload_hourly_rollup where tenant_id=:tenantId) last_rollup_refresh,
                       (select count(*) from analytics_workload_hourly_rollup where tenant_id=:tenantId) rollup_rows
                """,p,(rs,n)->new DashboardPerformanceStatus(rs.getLong("dirty_rollup_buckets"),rs.getObject("oldest_dirty_bucket",OffsetDateTime.class),rs.getObject("last_rollup_refresh",OffsetDateTime.class),rs.getLong("rollup_rows")));
    }


    /** Phase 7 plan-wide workload summary for Department/Group scoped operators. */
    public WorkloadSummary scopedWorkloadSummary(String tenantId, OffsetDateTime from, OffsetDateTime to, ResourceListScopeQueryPlan plan) {
        tenant(tenantId);
        var p = scopedFactParams(tenantId, from, to, plan);
        String predicate = scopedFactPredicate(plan, "f");
        return jdbc.queryForObject("""
                select count(*) total_tasks,
                       count(*) filter(where f.status in('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED','BLOCKED','RETRY_WAIT')) failed_tasks,
                       count(*) filter(where f.initial_priority in('P0','P1','CRITICAL','URGENT') or f.initial_severity='CRITICAL') critical_tasks,
                       count(*) filter(where f.origin_principal_type='USER') human_origin_tasks,
                       count(*) filter(where f.origin_principal_type='SERVICE_ACCOUNT') service_account_origin_tasks,
                       count(*) filter(where f.origin_principal_type='AGENT') agent_origin_tasks,
                       count(*) filter(where f.origin_principal_type='SYSTEM') system_origin_tasks,
                       count(distinct f.assigned_agent_id) filter(where f.assigned_agent_id is not null) distinct_agents,
                       count(distinct f.credential_id) filter(where f.credential_id is not null) distinct_credentials,
                       avg(f.duration_ms) filter(where f.duration_ms is not null) avg_duration_ms
                  from analytics_workload_fact f
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt
                   and """ + predicate,
                p, (rs, n) -> new WorkloadSummary(
                        rs.getLong("total_tasks"), rs.getLong("failed_tasks"), rs.getLong("critical_tasks"),
                        rs.getLong("human_origin_tasks"), rs.getLong("service_account_origin_tasks"), rs.getLong("agent_origin_tasks"), rs.getLong("system_origin_tasks"),
                        rs.getLong("distinct_agents"), rs.getLong("distinct_credentials"), number(rs.getObject("avg_duration_ms"))));
    }

    public List<BreakdownRow> scopedByOrigin(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit, ResourceListScopeQueryPlan plan) {
        return scopedBreakdown(tenantId, from, to, limit, plan, "coalesce(f.origin_principal_type,'UNKNOWN')");
    }

    public List<BreakdownRow> scopedByFailureDomain(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit, ResourceListScopeQueryPlan plan) {
        return scopedBreakdown(tenantId, from, to, limit, plan, "coalesce(f.failure_domain,'NONE')");
    }

    private List<BreakdownRow> scopedBreakdown(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit,
            ResourceListScopeQueryPlan plan, String dimensionExpression) {
        tenant(tenantId);
        var p = scopedFactParams(tenantId, from, to, plan).addValue("limit", bounded(limit));
        String predicate = scopedFactPredicate(plan, "f");
        String sql = "select " + dimensionExpression + " dimension_key,count(*) task_count," +
                " count(*) filter(where f.failure_domain<>'NONE') failure_count," +
                " count(*) filter(where f.initial_priority in('P0','P1','CRITICAL','URGENT') or f.initial_severity='CRITICAL') critical_count" +
                " from analytics_workload_fact f where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt and " + predicate +
                " group by " + dimensionExpression + " order by task_count desc,dimension_key limit :limit";
        return jdbc.query(sql, p, BREAKDOWN_MAPPER);
    }

    /** Uses fact data instead of Tenant-wide rollups so Resource Access can be enforced before aggregation. */
    public List<TrendRow> scopedWorkloadTrend(String tenantId, OffsetDateTime from, OffsetDateTime to, String bucket,
            ResourceListScopeQueryPlan plan) {
        tenant(tenantId);
        String normalized = bucket == null ? "DAY" : bucket.trim().toUpperCase();
        if (!normalized.equals("HOUR") && !normalized.equals("DAY")) throw new IllegalArgumentException("bucket must be HOUR or DAY");
        var p = scopedFactParams(tenantId, from, to, plan).addValue("bucket", normalized);
        String predicate = scopedFactPredicate(plan, "f");
        return jdbc.query("""
                select case when :bucket='HOUR' then date_trunc('hour',f.occurred_at) else date_trunc('day',f.occurred_at) end bucket_start,
                       count(*) task_count,
                       count(*) filter(where f.failure_domain<>'NONE') failure_count,
                       count(*) filter(where f.initial_priority in('P0','P1','CRITICAL','URGENT') or f.initial_severity='CRITICAL') critical_count,
                       avg(f.duration_ms) filter(where f.duration_ms is not null) avg_duration_ms
                  from analytics_workload_fact f
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt
                   and """ + predicate + """
                 group by case when :bucket='HOUR' then date_trunc('hour',f.occurred_at) else date_trunc('day',f.occurred_at) end
                 order by bucket_start
                """, p, (rs,n)->new TrendRow(rs.getObject("bucket_start",OffsetDateTime.class),rs.getLong("task_count"),rs.getLong("failure_count"),rs.getLong("critical_count"),number(rs.getObject("avg_duration_ms"))));
    }

    public List<RankedWorkloadRow> scopedByAgent(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit, ResourceListScopeQueryPlan plan) {
        return scopedRanking(tenantId,from,to,limit,plan,"AGENT");
    }
    public List<RankedWorkloadRow> scopedByCredential(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit, ResourceListScopeQueryPlan plan) {
        return scopedRanking(tenantId,from,to,limit,plan,"CREDENTIAL");
    }
    public List<RankedWorkloadRow> scopedBySourceSystem(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit, ResourceListScopeQueryPlan plan) {
        return scopedRanking(tenantId,from,to,limit,plan,"SOURCE_SYSTEM");
    }

    private List<RankedWorkloadRow> scopedRanking(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit,
            ResourceListScopeQueryPlan plan, String dimension) {
        tenant(tenantId);
        var p=scopedFactParams(tenantId,from,to,plan).addValue("limit",bounded(limit));
        String predicate=scopedFactPredicate(plan,"f");
        String key; String label; String join; String present;
        switch(dimension) {
            case "AGENT" -> { key="f.assigned_agent_id"; label="coalesce(max(p.agent_name),f.assigned_agent_id)"; join=" left join agent_profiles p on p.tenant_id=f.tenant_id and p.agent_id=f.assigned_agent_id "; present="f.assigned_agent_id is not null"; }
            case "CREDENTIAL" -> { key="f.credential_id"; label="coalesce(max(c.credential_name),f.credential_id)"; join=" left join token_service_account_credentials c on c.tenant_id=f.tenant_id and c.credential_id=f.credential_id "; present="f.credential_id is not null"; }
            case "SOURCE_SYSTEM" -> { key="f.source_system"; label="coalesce(max(s.display_name),f.source_system)"; join=" left join source_systems s on s.tenant_id=f.tenant_id and s.source_system_id=f.source_system "; present="f.source_system is not null and f.source_system<>''"; }
            default -> throw new IllegalArgumentException("Unsupported analytics ranking dimension");
        }
        String sql="select "+key+" dimension_key,"+label+" dimension_label,"+
                " count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,"+
                " count(*) filter(where f.initial_priority in('P0','P1','CRITICAL','URGENT') or f.initial_severity='CRITICAL') critical_count"+
                " from analytics_workload_fact f "+join+
                " where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt and "+present+" and "+predicate+
                " group by "+key+" order by failure_count desc,task_count desc,dimension_key limit :limit";
        return jdbc.query(sql,p,RANKED_MAPPER);
    }

    public RecentWorkloadPage scopedRecentWorkloads(String tenantId, OffsetDateTime from, OffsetDateTime to, int limit, String cursor,
            ResourceListScopeQueryPlan plan) {
        tenant(tenantId);
        var p=scopedFactParams(tenantId,from,to,plan);
        CursorPoint point=decodeCursor(cursor);
        int boundedLimit=Math.max(1,Math.min(limit,200));
        p.addValue("afterAt",point==null?null:point.occurredAt()).addValue("afterId",point==null?null:point.taskId()).addValue("limit",boundedLimit+1);
        String predicate=scopedFactPredicate(plan,"f");
        var rows=jdbc.query("""
                select f.task_id,f.occurred_at,f.status,f.origin_principal_type,f.origin_principal_id,f.origin_department_id,f.origin_group_id,
                       f.assigned_agent_id,f.credential_id,f.source_system,f.failure_domain,f.failure_code,f.initial_priority,f.initial_severity
                  from analytics_workload_fact f
                 where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt
                   and """+predicate+"""
                   and (:afterAt is null or f.occurred_at<:afterAt or (f.occurred_at=:afterAt and f.task_id<:afterId))
                 order by f.occurred_at desc,f.task_id desc limit :limit
                """,p,(rs,n)->new RecentWorkloadRow(rs.getString("task_id"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getString("status"),rs.getString("origin_principal_type"),rs.getString("origin_principal_id"),rs.getString("origin_department_id"),rs.getString("origin_group_id"),rs.getString("assigned_agent_id"),rs.getString("credential_id"),rs.getString("source_system"),rs.getString("failure_domain"),rs.getString("failure_code"),rs.getString("initial_priority"),rs.getString("initial_severity")));
        boolean more=rows.size()>boundedLimit;
        var page=more?rows.subList(0,boundedLimit):rows;
        String next=more&&!page.isEmpty()?encodeCursor(page.get(page.size()-1).occurredAt(),page.get(page.size()-1).taskId()):"";
        return new RecentWorkloadPage(List.copyOf(page),next,more);
    }

    private MapSqlParameterSource scopedFactParams(String tenantId,OffsetDateTime from,OffsetDateTime to,ResourceListScopeQueryPlan plan) {
        var params=base(tenantId,from,to);
        ScopedResourceSql.bind(params,plan);
        return params;
    }
    private static String scopedFactPredicate(ResourceListScopeQueryPlan plan,String alias) {
        return ScopedResourceSql.predicate(plan,alias,"task_id","origin_department_id","origin_group_id");
    }

    /** Phase 7 server-owned analytics scope projection. It exposes only current authorized organization roots, never raw Role bindings. */
    public AnalyticsAccessProfile accessProfile(String tenantId, ResourceListScopeQueryPlan plan) {
        tenant(tenantId);
        if (plan == null || plan.denyAll()) return new AnalyticsAccessProfile(false,true,false,"","",List.of(),List.of(),plan == null ? "" : plan.planHash());
        List<AnalyticsScopeOption> departments = new ArrayList<>();
        Set<String> departmentIds = new java.util.LinkedHashSet<>();
        departmentIds.addAll(plan.exactDepartmentIds());
        departmentIds.addAll(plan.subtreeDepartmentRootIds());
        if (!departmentIds.isEmpty()) {
            var params = new MapSqlParameterSource("tenantId",tenantId).addValue("ids",departmentIds);
            Map<String,AnalyticsScopeOption> labels = new LinkedHashMap<>();
            for (AnalyticsScopeOption option : jdbc.query("""
                    select department_id,department_name from analytics_dim_department_history
                     where tenant_id=:tenantId and is_current and department_id in (:ids)
                     order by department_name,department_id
                    """,params,(rs,n)->new AnalyticsScopeOption("DEPARTMENT",rs.getString("department_id"),rs.getString("department_name")))) labels.put(option.id(), option);
            departmentIds.stream().sorted().forEach(id -> departments.add(labels.getOrDefault(id,new AnalyticsScopeOption("DEPARTMENT",id,id))));
        }
        List<AnalyticsScopeOption> groups = new ArrayList<>();
        if (!plan.groupIds().isEmpty()) {
            var params = new MapSqlParameterSource("tenantId",tenantId).addValue("ids",plan.groupIds());
            Map<String,AnalyticsScopeOption> labels = new LinkedHashMap<>();
            for (AnalyticsScopeOption option : jdbc.query("""
                    select group_id,group_name from analytics_dim_group_history
                     where tenant_id=:tenantId and is_current and group_id in (:ids)
                     order by group_name,group_id
                    """,params,(rs,n)->new AnalyticsScopeOption("GROUP",rs.getString("group_id"),rs.getString("group_name")))) labels.put(option.id(), option);
            plan.groupIds().stream().sorted().forEach(id -> groups.add(labels.getOrDefault(id,new AnalyticsScopeOption("GROUP",id,id))));
        }
        // The aggregate view can evaluate the complete trusted Resource Access plan. A scoped user therefore
        // never needs to manufacture a Tenant-wide query or pick one arbitrary scope before seeing analytics.
        return new AnalyticsAccessProfile(plan.tenantWide(),false,false,"","",List.copyOf(departments),List.copyOf(groups),plan.planHash());
    }

    /** Scope-aware, searchable organization browser. It compiles the trusted Resource Access plan into SQL. */
    public AnalyticsOrganizationPage organizationPage(String tenantId, OffsetDateTime from, OffsetDateTime to,
            String kind, String departmentId, String text, int page, int size, ResourceListScopeQueryPlan plan) {
        tenant(tenantId);
        int boundedSize=Math.max(1,Math.min(size,100));
        int boundedPage=Math.max(0,page);
        var params=base(tenantId,from,to)
                .addValue("departmentId",text(departmentId))
                .addValue("search",text(text)==null?null:"%"+text(text).toLowerCase()+"%")
                .addValue("limit",boundedSize+1)
                .addValue("offset",boundedPage*boundedSize);
        ScopedResourceSql.bind(params,plan);
        String predicate=ScopedResourceSql.predicate(plan,"f","task_id","origin_department_id","origin_group_id");
        List<OrganizationRow> rows;
        if ("GROUP".equalsIgnoreCase(kind)) {
            rows=jdbc.query("""
                    select coalesce(f.origin_group_id,'UNASSIGNED') department_id,
                           coalesce(max(g.group_name),'Unassigned / historical') department_name,
                           count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,
                           count(distinct f.assigned_agent_id) filter(where f.assigned_agent_id is not null) agent_count
                      from analytics_workload_fact f
                      left join analytics_dim_group_history g on g.tenant_id=f.tenant_id and g.group_id=f.origin_group_id and g.dimension_version_id=f.origin_group_version_id
                     where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt
                       and (:departmentId is null or f.origin_department_id=:departmentId)
                       and (:search is null or lower(coalesce(g.group_name,f.origin_group_id,'')) like :search)
                       and """+predicate+"""
                     group by coalesce(f.origin_group_id,'UNASSIGNED')
                     order by task_count desc,department_id
                     limit :limit offset :offset
                    """,params,(rs,n)->new OrganizationRow(rs.getString("department_id"),rs.getString("department_name"),rs.getLong("task_count"),rs.getLong("failure_count"),rs.getLong("agent_count")));
        } else {
            rows=jdbc.query("""
                    select coalesce(f.origin_department_id,'UNASSIGNED') department_id,
                           coalesce(max(d.department_name),'Unassigned / historical') department_name,
                           count(*) task_count,count(*) filter(where f.failure_domain<>'NONE') failure_count,
                           count(distinct f.assigned_agent_id) filter(where f.assigned_agent_id is not null) agent_count
                      from analytics_workload_fact f
                      left join analytics_dim_department_history d on d.tenant_id=f.tenant_id and d.department_id=f.origin_department_id and d.dimension_version_id=f.origin_department_version_id
                     where f.tenant_id=:tenantId and f.occurred_at>=:fromAt and f.occurred_at<:toAt
                       and (:search is null or lower(coalesce(d.department_name,f.origin_department_id,'')) like :search)
                       and """+predicate+"""
                     group by coalesce(f.origin_department_id,'UNASSIGNED')
                     order by task_count desc,department_id
                     limit :limit offset :offset
                    """,params,(rs,n)->new OrganizationRow(rs.getString("department_id"),rs.getString("department_name"),rs.getLong("task_count"),rs.getLong("failure_count"),rs.getLong("agent_count")));
        }
        boolean more=rows.size()>boundedSize;
        List<OrganizationRow> items=more?List.copyOf(rows.subList(0,boundedSize)):List.copyOf(rows);
        return new AnalyticsOrganizationPage(items,boundedPage,boundedSize,more);
    }

    public static long contentRevision(Object value) {
        return Integer.toUnsignedLong(java.util.Objects.hashCode(value));
    }

    private static String encodeCursor(OffsetDateTime at,String taskId) {
        String raw=at.toString()+"|"+taskId;
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static CursorPoint decodeCursor(String cursor) {
        if(cursor==null||cursor.isBlank()) return null;
        try {
            String raw=new String(java.util.Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8);
            int split=raw.indexOf('|'); if(split<=0||split==raw.length()-1) throw new IllegalArgumentException();
            return new CursorPoint(OffsetDateTime.parse(raw.substring(0,split)),raw.substring(split+1));
        } catch(RuntimeException ex) { throw new IllegalArgumentException("Invalid analytics cursor"); }
    }

    private void tenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenantId);
    }

    private QueryScope scope(String tenantId,OffsetDateTime from,OffsetDateTime to,String departmentId,String groupId) {
        return new QueryScope(base(tenantId,from,to).addValue("departmentId",text(departmentId)).addValue("groupId",text(groupId)));
    }
    private MapSqlParameterSource base(String tenantId,OffsetDateTime from,OffsetDateTime to) {
        OffsetDateTime end=to==null?OffsetDateTime.now():to;
        OffsetDateTime start=from==null?end.minusDays(30):from;
        if(!start.isBefore(end)) throw new IllegalArgumentException("from must be earlier than to");
        if(start.plusDays(400).isBefore(end)) throw new IllegalArgumentException("analytics query window cannot exceed 400 days");
        return new MapSqlParameterSource("tenantId",tenantId).addValue("fromAt",start).addValue("toAt",end);
    }
    private static String text(String v){return v==null||v.isBlank()?null:v.trim();}
    private static int bounded(int v){return Math.max(1,Math.min(v,500));}
    private static Double number(Object value){return value==null?null:((Number)value).doubleValue();}

    private static final org.springframework.jdbc.core.RowMapper<BreakdownRow> BREAKDOWN_MAPPER=(rs,n)->new BreakdownRow(rs.getString("dimension_key"),rs.getLong("task_count"),rs.getLong("failure_count"),rs.getLong("critical_count"));
    private static final org.springframework.jdbc.core.RowMapper<RankedWorkloadRow> RANKED_MAPPER=(rs,n)->{long tasks=rs.getLong("task_count"),failures=rs.getLong("failure_count");return new RankedWorkloadRow(rs.getString("dimension_key"),rs.getString("dimension_label"),tasks,failures,rs.getLong("critical_count"),tasks==0?0d:(failures*100d/tasks));};
    private record QueryScope(MapSqlParameterSource params){ QueryScope withLimit(int limit){params.addValue("limit",bounded(limit));return this;} }

    public record ProjectionStatus(String projectionName,String lastEventId,OffsetDateTime lastEventAt,OffsetDateTime lastProjectedAt,long projectedEventCount,long failureCount,boolean rebuildRequired,long pendingEvents,long failedEvents,Double lagSeconds,OffsetDateTime updatedAt){}
    public record WorkloadSummary(long totalTasks,long failedTasks,long criticalTasks,long humanOriginTasks,long serviceAccountOriginTasks,long agentOriginTasks,long systemOriginTasks,long distinctAgents,long distinctCredentials,Double averageDurationMs){}
    public record BreakdownRow(String key,long taskCount,long failureCount,long criticalCount){}
    public record OrganizationRow(String departmentId,String departmentName,long taskCount,long failureCount,long distinctAgents){}
    public record SecuritySummary(long incidentCount,long openIncidentCount,long criticalIncidentCount,long controlActionCount,Double averageContainmentMs,Double averageResolutionMs){}
    public record TrendRow(OffsetDateTime bucketStart,long taskCount,long failureCount,long criticalCount,Double averageDurationMs){}
    public record RankedWorkloadRow(String key,String label,long taskCount,long failureCount,long criticalCount,double failureRatePercent){}
    public record SecurityOrganizationRow(String departmentId,String departmentName,long incidentCount,long openIncidentCount,long criticalIncidentCount,Double averageContainmentMs,Double averageResolutionMs){}
    public record RecentWorkloadRow(String taskId,OffsetDateTime occurredAt,String status,String originPrincipalType,String originPrincipalId,String departmentId,String groupId,String assignedAgentId,String credentialId,String sourceSystem,String failureDomain,String failureCode,String initialPriority,String initialSeverity){}
    public record RecentWorkloadPage(List<RecentWorkloadRow> items,String nextCursor,boolean hasMore){}
    public record DashboardPerformanceStatus(long dirtyRollupBuckets,OffsetDateTime oldestDirtyBucket,OffsetDateTime lastRollupRefresh,long rollupRows){}
    public record AnalyticsScopeOption(String scopeType,String id,String label){}
    public record AnalyticsAccessProfile(boolean tenantWide,boolean denied,boolean requiresScopeSelection,String defaultScopeType,String defaultScopeId,List<AnalyticsScopeOption> departments,List<AnalyticsScopeOption> groups,String planHash){}
    public record AnalyticsOrganizationPage(List<OrganizationRow> items,int page,int size,boolean hasMore){}
    private record CursorPoint(OffsetDateTime occurredAt,String taskId){}
}
