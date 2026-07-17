package com.opensocket.aievent.database.persistence.dispatch.governance.repository;

import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.routing.governance.routing.GenericCandidateAgentRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class JdbcGenericCandidateAgentRepository implements GenericCandidateAgentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcGenericCandidateAgentRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override
    public List<String> findExplicitFlowAgentIds(String tenantId,String flowId,String eventStage,int limit){
        String requiredTenantId = require(tenantId,"tenantId");
        String requiredFlowId = require(flowId,"flowId");
        return jdbc.queryForList("""
            select distinct agent_id
              from flow_agent_assignments
             where upper(replace(replace(replace(btrim(tenant_id),'-','_'),'.','_'),' ','_')) = :tenantIdNormalized
               and upper(replace(replace(replace(btrim(flow_id),'-','_'),'.','_'),' ','_')) = :flowIdNormalized
               and upper(coalesce(event_stage,'EXTERNAL')) in (:eventStage,'*')
               and upper(coalesce(assignment_status,'DRAFT')) in ('ACTIVE','ENABLED')
               and upper(coalesce(approval_status,'PENDING')) in ('APPROVED','ACTIVE')
               and agent_id is not null and btrim(agent_id)<>''
             order by agent_id
             limit :limit
            """,new MapSqlParameterSource().addValue("tenantIdNormalized",normalize(requiredTenantId))
                .addValue("flowIdNormalized",normalize(requiredFlowId)).addValue("eventStage",normalize(defaultValue(eventStage,"EXTERNAL")))
                .addValue("limit",bounded(limit)),String.class);
    }

    private static int bounded(int v){return Math.max(1,Math.min(v<=0?500:v,2000));}
    private static String require(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v;}
    private static String defaultValue(String v,String d){return v==null||v.isBlank()?d:v;}
    private static String normalize(String v){return v==null?"":v.trim().replace('-','_').replace('.','_').replace(' ','_').toUpperCase(Locale.ROOT);}
}
