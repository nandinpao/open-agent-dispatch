package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable Stage 6 MCP execution claim/update authority. */
@Service
public class McpReadExecutionQueueService {
    private final NamedParameterJdbcTemplate jdbc;
    public McpReadExecutionQueueService(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public List<Item> claimDue(String tenant,String worker,int limit){
        bind(tenant); OffsetDateTime now=OffsetDateTime.now(); OffsetDateTime until=now.plusSeconds(60); int cap=Math.max(1,Math.min(limit,50));
        return jdbc.query("""
          with due as (
            select execution_id from mcp_read_executions
             where tenant_id=:tenant and status in ('QUEUED','FAILED') and attempts<max_attempts
               and (next_attempt_at is null or next_attempt_at<=:now) and (claim_until is null or claim_until<:now)
             order by coalesce(next_attempt_at,created_at),execution_id for update skip locked limit :limit
          )
          update mcp_read_executions e set status='PROCESSING',claimed_by=:worker,claim_until=:until,attempts=attempts+1,updated_at=:now
           from due where e.tenant_id=:tenant and e.execution_id=due.execution_id
          returning e.execution_id,e.execution_context_type,e.delegation_id,e.plan_run_id,e.plan_step_id,e.plan_attempt_id,e.task_id,e.assignment_id,e.provider_id,e.endpoint_url,e.request_json::text,e.attempts,e.max_attempts
          """,new MapSqlParameterSource("tenant",tenant).addValue("worker",worker).addValue("until",until).addValue("now",now).addValue("limit",cap),
          (rs,n)->new Item(rs.getString("execution_id"),rs.getString("execution_context_type"),rs.getString("delegation_id"),rs.getString("plan_run_id"),rs.getString("plan_step_id"),rs.getString("plan_attempt_id"),rs.getString("task_id"),rs.getString("assignment_id"),rs.getString("provider_id"),rs.getString("endpoint_url"),rs.getString("request_json"),rs.getInt("attempts"),rs.getInt("max_attempts")));
    }
    @Transactional public void success(String tenant,String execution,int http,String response){bind(tenant);jdbc.update("update mcp_read_executions set status='SUCCEEDED',http_status=:http,response_json=cast(:response as jsonb),claimed_by=null,claim_until=null,completed_at=:now,updated_at=:now where tenant_id=:tenant and execution_id=:execution",new MapSqlParameterSource("tenant",tenant).addValue("execution",execution).addValue("http",http).addValue("response",response).addValue("now",OffsetDateTime.now()));}
    @Transactional public void failure(String tenant,String execution,String code,String message,int attempt,int max){bind(tenant);boolean dead=attempt>=max;OffsetDateTime now=OffsetDateTime.now();jdbc.update("update mcp_read_executions set status=:status,error_code=:code,error_message=:message,next_attempt_at=:next,claimed_by=null,claim_until=null,updated_at=:now where tenant_id=:tenant and execution_id=:execution",new MapSqlParameterSource("tenant",tenant).addValue("execution",execution).addValue("status",dead?"DEAD_LETTER":"FAILED").addValue("code",code).addValue("message",message).addValue("next",dead?null:now.plusSeconds(Math.min(60,5L*attempt))).addValue("now",now));}
    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"mcp-read-worker");}
    public record Item(String executionId,String executionContextType,String delegationId,String planRunId,String planStepId,String planAttemptId,String taskId,String assignmentId,String providerId,String endpointUrl,String requestJson,int attempt,int maxAttempts){}
}
