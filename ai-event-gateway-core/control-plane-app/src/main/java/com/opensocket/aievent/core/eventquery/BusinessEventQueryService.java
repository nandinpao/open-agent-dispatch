package com.opensocket.aievent.core.eventquery;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedResourceSql;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** RS2 query adapter. Scope filtering is compiled into SQL before rows are returned. */
@Service
public final class BusinessEventQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    public BusinessEventQueryService(NamedParameterJdbcTemplate jdbc,ObjectMapper objectMapper){this.jdbc=Objects.requireNonNull(jdbc);this.objectMapper=Objects.requireNonNull(objectMapper);}

    public List<BusinessEventView> list(ResourceListScopeQueryPlan plan,String sourceSystem,String eventType,int requestedLimit){
        int limit=Math.max(1,Math.min(requestedLimit,500));
        MapSqlParameterSource params=new MapSqlParameterSource().addValue("tenantId",plan.tenantId()).addValue("limit",limit);
        StringBuilder sql=new StringBuilder("""
            select event_id,tenant_id,source_system,event_type,event_stage,correlation_id,normalized_message,
                   decision_type,duplicate,occurrence_count,incident_id,occurred_at,decided_at,
                   owner_department_id,owner_group_id,scope_status,scope_source_version,scope_inherited_at
              from event_decisions e
             where e.tenant_id=:tenantId and e.event_id is not null and e.scope_status <> 'UNRESOLVED' and 
            """);
        sql.append(ScopedResourceSql.predicate(plan,"e","event_id","owner_department_id","owner_group_id"));
        if(sourceSystem!=null&&!sourceSystem.isBlank()){sql.append(" and e.source_system=:sourceSystem");params.addValue("sourceSystem",sourceSystem.trim());}
        if(eventType!=null&&!eventType.isBlank()){sql.append(" and e.event_type=:eventType");params.addValue("eventType",eventType.trim());}
        sql.append(" order by e.decided_at desc limit :limit");
        ScopedResourceSql.bind(params,plan);
        return jdbc.query(sql.toString(),params,(rs,n)->map(rs));
    }

    public Optional<BusinessEventView> find(String tenantId,String eventId){
        try{return Optional.ofNullable(jdbc.queryForObject("""
            select event_id,tenant_id,source_system,event_type,event_stage,correlation_id,normalized_message,
                   decision_type,duplicate,occurrence_count,incident_id,occurred_at,decided_at,
                   owner_department_id,owner_group_id,scope_status,scope_source_version,scope_inherited_at
              from event_decisions where tenant_id=:tenantId and event_id=:eventId
            """,new MapSqlParameterSource().addValue("tenantId",tenantId).addValue("eventId",eventId),(rs,n)->map(rs)));}
        catch(EmptyResultDataAccessException ex){return Optional.empty();}
    }

    public Optional<BusinessEventPayloadView> payload(String tenantId,String eventId){
        try{return Optional.ofNullable(jdbc.queryForObject("""
            select event_id,payload_json::text payload_json from event_decisions where tenant_id=:tenantId and event_id=:eventId
            """,new MapSqlParameterSource().addValue("tenantId",tenantId).addValue("eventId",eventId),(rs,n)->new BusinessEventPayloadView(rs.getString("event_id"),readPayload(rs.getString("payload_json")))));}
        catch(EmptyResultDataAccessException ex){return Optional.empty();}
    }

    private BusinessEventView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Number sourceVersion=(Number)rs.getObject("scope_source_version");
        return new BusinessEventView(rs.getString("event_id"),rs.getString("tenant_id"),rs.getString("source_system"),rs.getString("event_type"),
                rs.getString("event_stage"),rs.getString("correlation_id"),rs.getString("normalized_message"),rs.getString("decision_type"),rs.getBoolean("duplicate"),
                rs.getLong("occurrence_count"),rs.getString("incident_id"),rs.getObject("occurred_at",OffsetDateTime.class),rs.getObject("decided_at",OffsetDateTime.class),
                rs.getString("owner_department_id"),rs.getString("owner_group_id"),rs.getString("scope_status"),sourceVersion==null?null:sourceVersion.longValue(),rs.getObject("scope_inherited_at",OffsetDateTime.class));
    }
    @SuppressWarnings("unchecked") private Map<String,Object> readPayload(String json){
        try{if(json==null||json.isBlank())return Map.of();return objectMapper.readValue(json,Map.class);}catch(Exception ex){throw new IllegalStateException("Cannot deserialize Business Event payload",ex);}
    }
}
