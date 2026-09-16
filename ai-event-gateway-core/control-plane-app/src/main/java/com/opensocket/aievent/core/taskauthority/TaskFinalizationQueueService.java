package com.opensocket.aievent.core.taskauthority;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A0-R2 bounded SKIP LOCKED queue for canonical FINALIZING tasks. */
@Service
public class TaskFinalizationQueueService {
    private final NamedParameterJdbcTemplate jdbc;
    private final String workerId;
    private final int batchSize;
    private final int claimSeconds;

    public TaskFinalizationQueueService(NamedParameterJdbcTemplate jdbc,
            @Value("${opendispatch.a0-r2.finalization.worker-id:}") String workerId,
            @Value("${opendispatch.a0-r2.finalization.batch-size:20}") int batchSize,
            @Value("${opendispatch.a0-r2.finalization.claim-seconds:60}") int claimSeconds) {
        this.jdbc=jdbc; this.workerId=(workerId==null||workerId.isBlank()) ? "a0-r2-finalizer-"+java.util.UUID.randomUUID().toString().substring(0,12) : workerId.trim(); this.batchSize=Math.max(1,Math.min(batchSize,100));
        this.claimSeconds=Math.max(15,Math.min(claimSeconds,300));
    }

    @Transactional
    public List<Item> claimDue(String tenant) {
        bind(tenant); OffsetDateTime now=OffsetDateTime.now(); OffsetDateTime until=now.plusSeconds(claimSeconds);
        return jdbc.query("""
          with due as (
            select task_id from tasks
             where tenant_id=:tenant and task_lifecycle='FINALIZING'
               and (
                    (finalization_state in ('PENDING','RETRY_PENDING') and coalesce(finalization_next_attempt_at,now())<=:now)
                    or (finalization_state='RUNNING' and finalization_claim_until<=:now)
               )
               and (finalization_claim_until is null or finalization_claim_until<=:now)
             order by coalesce(finalization_next_attempt_at,terminalization_requested_at,updated_at),task_id
             for update skip locked limit :limit
          )
          update tasks t set finalization_state='RUNNING',finalization_claimed_by=:worker,
                 finalization_claim_until=:until,finalization_attempt_count=finalization_attempt_count+1,
                 finalization_last_error_code=null,finalization_last_error_message=null,updated_at=:now
            from due where t.tenant_id=:tenant and t.task_id=due.task_id
          returning t.task_id,t.finalization_attempt_count,t.terminalization_reason,t.status
          """,params(tenant).addValue("worker",workerId).addValue("now",now).addValue("until",until).addValue("limit",batchSize),
          (rs,n)->new Item(rs.getString("task_id"),rs.getInt("finalization_attempt_count"),rs.getString("terminalization_reason"),rs.getString("status")));
    }

    @Transactional
    public void fail(String tenant,Item item,String code,String message,int maxAttempts) {
        bind(tenant); boolean manual=item.attempt()>=Math.max(1,maxAttempts); OffsetDateTime now=OffsetDateTime.now();
        long backoff=Math.min(900L,Math.max(5L,5L << Math.min(7,Math.max(0,item.attempt()-1))));
        jdbc.update("""
          update tasks set finalization_state=:state,finalization_claimed_by=null,finalization_claim_until=null,
                 finalization_next_attempt_at=:next,finalization_last_error_code=:code,finalization_last_error_message=:message,
                 updated_at=:now where tenant_id=:tenant and task_id=:task and task_lifecycle='FINALIZING'
          """,params(tenant).addValue("task",item.taskId()).addValue("state",manual?"MANUAL_RECOVERY_REQUIRED":"RETRY_PENDING")
             .addValue("next",manual?null:now.plusSeconds(backoff)).addValue("code",safe(code,160)).addValue("message",safe(message,2000)).addValue("now",now));
        String condition=manual?"MANUAL_RECOVERY_REQUIRED":"FINALIZATION_RETRY_PENDING";
        jdbc.update("""
          insert into task_conditions(tenant_id,task_id,condition_type,blocking,resolution,timeout_action,reason_code,reason,raised_by)
          values(:tenant,:task,:condition,false,:resolution,'NONE',:code,:message,:worker)
          on conflict(tenant_id,task_id,condition_type) do update set condition_state='ACTIVE',reason_code=excluded.reason_code,
              reason=excluded.reason,raised_by=excluded.raised_by,raised_at=now(),resolved_by=null,resolved_at=null,resolution_note=null,version=task_conditions.version+1
          """,params(tenant).addValue("task",item.taskId()).addValue("condition",condition)
              .addValue("resolution",manual?"REQUIRE_HUMAN":"WAIT").addValue("code",safe(code,160)).addValue("message",safe(message,2000)).addValue("worker",workerId));
        jdbc.update("""
          insert into task_finalization_events(tenant_id,event_id,task_id,event_type,finalization_state,actor_ref,evidence_json)
          values(:tenant,:event,:task,'FINALIZATION_FAILED',:state,:worker,cast(:evidence as jsonb))
          """,params(tenant).addValue("event",eventId("finfail",item.taskId())).addValue("task",item.taskId())
              .addValue("state",manual?"MANUAL_RECOVERY_REQUIRED":"RETRY_PENDING").addValue("worker",workerId)
              .addValue("evidence","{\"errorCode\":\""+json(safe(code,160))+"\"}"));
    }

    public String workerId(){return workerId;}
    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,workerId);}
    private static MapSqlParameterSource params(String tenant){return new MapSqlParameterSource("tenant",tenant);}
    private static String eventId(String prefix,String task){return prefix+"-"+task+"-"+java.util.UUID.randomUUID();}
    private static String safe(String v,int n){if(v==null)return "";return v.length()<=n?v:v.substring(0,n);}
    private static String json(String v){return v.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    public record Item(String taskId,int attempt,String terminalizationReason,String legacyStatus){}
}
