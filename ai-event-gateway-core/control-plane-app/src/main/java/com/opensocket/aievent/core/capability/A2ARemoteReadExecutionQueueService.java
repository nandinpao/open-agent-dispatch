package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable Stage 7 initial remote A2A send queue. C0-B6 persists canonical peer-error semantics. */
@Service
public class A2ARemoteReadExecutionQueueService {
    private final NamedParameterJdbcTemplate jdbc;
    public A2ARemoteReadExecutionQueueService(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public List<Item> claimDue(String tenant,String worker,int limit){
        bind(tenant);OffsetDateTime now=OffsetDateTime.now(),until=now.plusSeconds(60);
        return jdbc.query("""
          with due as (
            select execution_id from a2a_remote_read_executions
             where tenant_id=:tenant and status in ('QUEUED','FAILED') and remote_task_id is null
               and attempts<max_attempts and (next_attempt_at is null or next_attempt_at<=:now)
               and (claim_until is null or claim_until<:now)
             order by coalesce(next_attempt_at,created_at),execution_id for update skip locked limit :limit
          )
          update a2a_remote_read_executions e
             set status='PROCESSING',claimed_by=:worker,claim_until=:until,attempts=attempts+1,updated_at=:now
            from due where e.tenant_id=:tenant and e.execution_id=due.execution_id
          returning e.execution_id,e.execution_context_type,e.delegation_id,e.plan_run_id,e.plan_step_id,e.plan_attempt_id,
                    e.task_id,e.assignment_id,e.provider_id,e.peer_id,e.interface_id,e.endpoint_url,e.interface_tenant,
                    e.selected_protocol_version,e.request_json::text,e.attempts,e.max_attempts
          """,new MapSqlParameterSource("tenant",tenant).addValue("worker",worker).addValue("until",until).addValue("now",now).addValue("limit",Math.max(1,Math.min(limit,50))),
          (rs,n)->new Item(rs.getString("execution_id"),rs.getString("execution_context_type"),rs.getString("delegation_id"),rs.getString("plan_run_id"),rs.getString("plan_step_id"),rs.getString("plan_attempt_id"),rs.getString("task_id"),rs.getString("assignment_id"),rs.getString("provider_id"),rs.getString("peer_id"),rs.getString("interface_id"),rs.getString("endpoint_url"),rs.getString("interface_tenant"),rs.getString("selected_protocol_version"),rs.getString("request_json"),rs.getInt("attempts"),rs.getInt("max_attempts")));
    }

    @Transactional
    public void waiting(String tenant,String execution,String remoteTask,String context,String state,String response){bind(tenant);jdbc.update("update a2a_remote_read_executions set status='WAITING_REMOTE',remote_task_id=:remoteTask,remote_context_id=:context,remote_state=:state,response_json=cast(:response as jsonb),claimed_by=null,claim_until=null,updated_at=:now where tenant_id=:tenant and execution_id=:execution",new MapSqlParameterSource("tenant",tenant).addValue("execution",execution).addValue("remoteTask",remoteTask).addValue("context",context).addValue("state",state).addValue("response",response).addValue("now",OffsetDateTime.now()));}
    @Transactional
    public void success(String tenant,String execution,String state,String response){bind(tenant);jdbc.update("update a2a_remote_read_executions set status='SUCCEEDED',remote_state=:state,response_json=cast(:response as jsonb),claimed_by=null,claim_until=null,completed_at=:now,terminal_at=:now,updated_at=:now where tenant_id=:tenant and execution_id=:execution",new MapSqlParameterSource("tenant",tenant).addValue("execution",execution).addValue("state",state).addValue("response",response).addValue("now",OffsetDateTime.now()));}

    /** Legacy/local infrastructure failure path. Peer HTTP errors must use {@link #mappedFailure}. */
    @Transactional
    public FailureOutcome failure(String tenant,String execution,String code,String message,int attempt,int max){
        bind(tenant);boolean dead=attempt>=max;OffsetDateTime now=OffsetDateTime.now();jdbc.update("update a2a_remote_read_executions set status=:status,error_code=:code,error_message=:message,canonical_error_class='TEMPORARY',error_disposition='RETRY',error_mapping_source='BASELINE',next_attempt_at=:next,claimed_by=null,claim_until=null,updated_at=:now where tenant_id=:tenant and execution_id=:execution",new MapSqlParameterSource("tenant",tenant).addValue("execution",execution).addValue("status",dead?"DEAD_LETTER":"FAILED").addValue("code",code).addValue("message",message).addValue("next",dead?null:now.plusSeconds(Math.min(60,5L*Math.max(1,attempt)))).addValue("now",now));return new FailureOutcome(dead?"DEAD_LETTER":"FAILED",dead,!dead);
    }

    @Transactional
    public FailureOutcome mappedFailure(String tenant,String execution,A2APeerErrorMappingService.Resolution r,int attempt,int max){
        bind(tenant);boolean retryable=r.retryable(),exhausted=retryable&&attempt>=max;String status;OffsetDateTime next=null;boolean completeNow;
        if(r.blocking()){status="BLOCKED";completeNow=true;}
        else if(r.terminal()){status="DEAD_LETTER";completeNow=true;}
        else if(exhausted){status="DEAD_LETTER";completeNow=true;}
        else {status="FAILED";completeNow=false;next=OffsetDateTime.now().plusSeconds(Math.min(60,5L*Math.max(1,attempt)));}
        OffsetDateTime now=OffsetDateTime.now();jdbc.update("""
          update a2a_remote_read_executions
             set status=:status,error_code=:code,error_message=:message,
                 remote_error_code=:remoteCode,remote_error_message=:remoteMessage,
                 canonical_error_class=:errorClass,error_disposition=:disposition,error_mapping_source=:mappingSource,error_mapping_override_id=:override,
                 next_attempt_at=:next,claimed_by=null,claim_until=null,updated_at=:now
           where tenant_id=:tenant and execution_id=:execution
          """,new MapSqlParameterSource("tenant",tenant).addValue("execution",execution).addValue("status",status).addValue("code",r.canonicalErrorCode()).addValue("message",r.remoteErrorMessage()).addValue("remoteCode",r.remoteErrorCode()).addValue("remoteMessage",r.remoteErrorMessage()).addValue("errorClass",r.resolvedErrorClass()).addValue("disposition",r.disposition()).addValue("mappingSource",r.mappingSource()).addValue("override",r.overrideId()).addValue("next",next).addValue("now",now));
        return new FailureOutcome(status,completeNow,!completeNow);
    }

    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"a2a-read-send-worker");}
    public record FailureOutcome(String status,boolean completeNow,boolean retryScheduled){}
    public record Item(String executionId,String executionContextType,String delegationId,String planRunId,String planStepId,String planAttemptId,String taskId,String assignmentId,String providerId,String peerId,String interfaceId,String endpointUrl,String interfaceTenant,String protocolVersion,String requestJson,int attempt,int maxAttempts){}
}
