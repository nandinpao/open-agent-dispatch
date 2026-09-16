package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stage 8 operator read/cancel surface. It exposes evidence and safety actions, not provider selection. */
@Service
public class A2ARemoteOperationsService {
    private final NamedParameterJdbcTemplate jdbc; private final A2ARemoteCancellationService cancellation;
    public A2ARemoteOperationsService(NamedParameterJdbcTemplate jdbc,A2ARemoteCancellationService cancellation){this.jdbc=jdbc;this.cancellation=cancellation;}
    @Transactional(readOnly=true) public Map<String,Object> execution(String tenant,String executionId){bind(tenant);List<Map<String,Object>> rows=jdbc.queryForList("select * from a2a_remote_read_executions where tenant_id=:tenant and execution_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("id",executionId));if(rows.isEmpty())throw new IllegalArgumentException("A2A_REMOTE_EXECUTION_NOT_FOUND");return rows.get(0);}
    @Transactional(readOnly=true) public Map<String,Object> tracking(String tenant,String executionId){bind(tenant);List<Map<String,Object>> rows=jdbc.queryForList("select * from a2a_remote_tracking_leases where tenant_id=:tenant and execution_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("id",executionId));if(rows.isEmpty())throw new IllegalArgumentException("A2A_REMOTE_TRACKING_NOT_FOUND");return rows.get(0);}
    @Transactional(readOnly=true) public List<Map<String,Object>> journal(String tenant,String executionId,int limit){bind(tenant);return jdbc.queryForList("select journal_event_id,tracking_id,execution_id,remote_task_id,source,event_type,remote_state,remote_event_id,payload_hash,payload_json,local_remote_sequence,stream_id,is_authoritative,authority_epoch,lease_owner_instance_id,lease_token_fingerprint,authority_decision_reason,authority_contract_version,observed_at from a2a_remote_event_journal where tenant_id=:tenant and execution_id=:id order by observed_at desc,journal_id desc limit :limit",new MapSqlParameterSource("tenant",tenant).addValue("id",executionId).addValue("limit",Math.max(1,Math.min(limit,500))));}
    public void cancel(String tenant,String executionId){cancellation.request(tenant,executionId);}
    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"a2a-remote-operations");}
}
