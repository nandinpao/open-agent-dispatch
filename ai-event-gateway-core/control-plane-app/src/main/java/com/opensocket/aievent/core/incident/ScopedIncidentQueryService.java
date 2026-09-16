package com.opensocket.aievent.core.incident;

import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** RS2 server-side row scoped incident list adapter. */
@Service
public final class ScopedIncidentQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    public ScopedIncidentQueryService(NamedParameterJdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}
    public List<Incident> search(IncidentQuery query,ResourceListScopeQueryPlan plan){
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("tenantId",plan.tenantId()).addValue("limit",Math.max(1,Math.min(query.getLimit(),1000)));
        StringBuilder sql=new StringBuilder("select * from incidents i where i.tenant_id=:tenantId and i.scope_status <> 'UNRESOLVED' and ");
        sql.append(ScopedResourceSql.predicate(plan,"i","incident_id","owner_department_id","owner_group_id"));
        add(sql,p,"source_system","sourceSystem",query.getSourceSystem()); add(sql,p,"site_id","siteId",query.getSiteId()); add(sql,p,"plant_id","plantId",query.getPlantId());
        add(sql,p,"object_type","objectType",query.getObjectType()); add(sql,p,"object_id","objectId",query.getObjectId()); add(sql,p,"event_type","eventType",query.getEventType()); add(sql,p,"error_code","errorCode",query.getErrorCode());
        if(query.getSeverity()!=null){sql.append(" and i.severity=:severity");p.addValue("severity",query.getSeverity().name());}
        if(query.getStatus()!=null){sql.append(" and i.status=:status");p.addValue("status",query.getStatus().name());}
        sql.append(" order by i.last_seen_at desc limit :limit"); ScopedResourceSql.bind(p,plan);
        return jdbc.query(sql.toString(),p,(rs,n)->map(rs));
    }
    private void add(StringBuilder sql,MapSqlParameterSource p,String column,String parameter,String value){if(value!=null&&!value.isBlank()){sql.append(" and i.").append(column).append("=:").append(parameter);p.addValue(parameter,value.trim());}}
    private Incident map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Incident i=new Incident(); i.setIncidentId(rs.getString("incident_id")); i.setFingerprint(rs.getString("fingerprint")); i.setTenantId(rs.getString("tenant_id")); i.setSourceSystem(rs.getString("source_system"));
        i.setSiteId(rs.getString("site_id")); i.setPlantId(rs.getString("plant_id")); i.setObjectType(rs.getString("object_type")); i.setObjectId(rs.getString("object_id")); i.setEventType(rs.getString("event_type")); i.setErrorCode(rs.getString("error_code"));
        i.setSeverity(EventSeverity.parse(rs.getString("severity"))); String status=rs.getString("status"); i.setStatus(status==null?null:IncidentStatus.valueOf(status));
        i.setFirstSeenAt(rs.getObject("first_seen_at",OffsetDateTime.class)); i.setLastSeenAt(rs.getObject("last_seen_at",OffsetDateTime.class)); i.setOccurrenceCount(rs.getLong("occurrence_count")); i.setLastMessage(rs.getString("last_message"));
        i.setLinkedTaskId(rs.getString("linked_task_id")); i.setLinkedIssueId(rs.getString("linked_issue_id")); i.setResolvedAt(rs.getObject("resolved_at",OffsetDateTime.class)); i.setReopenedAt(rs.getObject("reopened_at",OffsetDateTime.class));
        i.setReopenCount(rs.getInt("reopen_count")); i.setLifecycleReason(rs.getString("lifecycle_reason")); i.setOwnerDepartmentId(rs.getString("owner_department_id")); i.setOwnerGroupId(rs.getString("owner_group_id")); i.setScopeStatus(rs.getString("scope_status"));
        Number v=(Number)rs.getObject("scope_source_version"); i.setScopeSourceVersion(v==null?null:v.longValue()); i.setScopeInheritedAt(rs.getObject("scope_inherited_at",OffsetDateTime.class)); return i;
    }
}
