package com.opensocket.aievent.core.taskauthority;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;

/** Operator read/recovery facade. Actor and tenant are always server-resolved IAM context. */
@Service
public class TaskFinalizationAdminService {
    private final JdbcTemplate jdbc;
    public TaskFinalizationAdminService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public View view(String taskId){var c=IamTenantContextHolder.require();bind(c.tenantId(),c.actorId());TaskState task=jdbc.queryForObject("""
      select task_id,status,task_lifecycle,task_phase,task_outcome,terminalization_reason,terminalization_requested_at,
             outcome_resolver_version,cancellation_state,finalization_state,finalization_checkpoint,finalization_attempt_count,
             finalization_next_attempt_at,finalization_last_error_code,finalization_last_error_message,finalization_completed_at,
             evidence_availability_requirement from tasks where tenant_id=? and task_id=?
      """,(rs,n)->new TaskState(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getObject(7,OffsetDateTime.class),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getInt(12),rs.getObject(13,OffsetDateTime.class),rs.getString(14),rs.getString(15),rs.getObject(16,OffsetDateTime.class),rs.getString(17)),c.tenantId(),taskId);if(task==null)throw new IllegalArgumentException("Task not found: "+taskId);
      List<Step> steps=jdbc.query("select step_name,step_order,step_status,attempt_count,last_error_code,last_error_message,completed_at,updated_at from task_finalization_steps where tenant_id=? and task_id=? order by step_order",(rs,n)->new Step(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getObject(7,OffsetDateTime.class),rs.getObject(8,OffsetDateTime.class)),c.tenantId(),taskId);
      List<Condition> conditions=jdbc.query("select condition_type,condition_state,blocking,resolution,timeout_at,reason_code,reason,raised_at,resolved_at from task_conditions where tenant_id=? and task_id=? order by condition_state,raised_at desc,condition_type",(rs,n)->new Condition(rs.getString(1),rs.getString(2),rs.getBoolean(3),rs.getString(4),rs.getObject(5,OffsetDateTime.class),rs.getString(6),rs.getString(7),rs.getObject(8,OffsetDateTime.class),rs.getObject(9,OffsetDateTime.class)),c.tenantId(),taskId);
      return new View(c.tenantId(),task,steps,conditions);
    }

    @Transactional
    public View retry(String taskId,String reason){var c=IamTenantContextHolder.require();bind(c.tenantId(),c.actorId());String why=(reason==null||reason.isBlank())?"Operator requested finalization retry":reason.trim();int n=jdbc.update("""
      update tasks set finalization_state='PENDING',finalization_next_attempt_at=now(),finalization_claimed_by=null,finalization_claim_until=null,
             finalization_last_error_code=null,finalization_last_error_message=null,updated_at=now(),lifecycle_reason=?
       where tenant_id=? and task_id=? and task_lifecycle='FINALIZING' and finalization_state in ('RETRY_PENDING','MANUAL_RECOVERY_REQUIRED','PENDING')
      """,why,c.tenantId(),taskId);if(n!=1)throw new IllegalStateException("TASK_FINALIZATION_RETRY_NOT_ALLOWED:"+taskId);jdbc.update("update task_conditions set condition_state='RESOLVED',resolved_by=?,resolved_at=now(),resolution_note=?,version=version+1 where tenant_id=? and task_id=? and condition_type in ('FINALIZATION_RETRY_PENDING','MANUAL_RECOVERY_REQUIRED','EVIDENCE_PERSISTENCE_BLOCKED','CASE_PERSISTENCE_BLOCKED') and condition_state='ACTIVE'",c.actorId(),why,c.tenantId(),taskId);jdbc.update("insert into task_finalization_events(tenant_id,event_id,task_id,event_type,finalization_state,actor_ref,evidence_json) values(?,concat('finretry-',md5(?||':'||clock_timestamp()::text||':'||random()::text)),?,'MANUAL_RETRY_REQUESTED','PENDING',?,jsonb_build_object('reason',?))",c.tenantId(),c.tenantId()+":"+taskId,taskId,c.actorId(),why);return view(taskId);}

    @Transactional(readOnly=true)
    public List<RecoveryItem> recoveryQueue(int limit){var c=IamTenantContextHolder.require();bind(c.tenantId(),c.actorId());return jdbc.query("""
      select task_id,status,terminalization_reason,finalization_state,finalization_checkpoint,finalization_attempt_count,
             finalization_next_attempt_at,finalization_last_error_code,finalization_last_error_message,terminalization_requested_at
        from tasks where tenant_id=? and task_lifecycle='FINALIZING' and finalization_state in ('RETRY_PENDING','MANUAL_RECOVERY_REQUIRED')
       order by case when finalization_state='MANUAL_RECOVERY_REQUIRED' then 0 else 1 end,coalesce(finalization_next_attempt_at,terminalization_requested_at),task_id limit ?
      """,(rs,n)->new RecoveryItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getObject(7,OffsetDateTime.class),rs.getString(8),rs.getString(9),rs.getObject(10,OffsetDateTime.class)),c.tenantId(),Math.max(1,Math.min(limit,500)));}

    private void bind(String tenant,String actor){jdbc.queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenant);jdbc.queryForObject("select set_config('app.current_actor_id',?,true)",String.class,actor);}
    public record View(String tenantId,TaskState task,List<Step> steps,List<Condition> conditions){}
    public record TaskState(String taskId,String legacyStatus,String lifecycle,String phase,String outcome,String terminalizationReason,OffsetDateTime terminalizationRequestedAt,String outcomeResolverVersion,String cancellationState,String finalizationState,String checkpoint,int attemptCount,OffsetDateTime nextAttemptAt,String lastErrorCode,String lastErrorMessage,OffsetDateTime completedAt,String evidenceRequirement){}
    public record Step(String name,int order,String status,int attempts,String lastErrorCode,String lastErrorMessage,OffsetDateTime completedAt,OffsetDateTime updatedAt){}
    public record Condition(String type,String state,boolean blocking,String resolution,OffsetDateTime timeoutAt,String reasonCode,String reason,OffsetDateTime raisedAt,OffsetDateTime resolvedAt){}
    public record RecoveryItem(String taskId,String legacyStatus,String terminalizationReason,String finalizationState,String checkpoint,int attemptCount,OffsetDateTime nextAttemptAt,String lastErrorCode,String lastErrorMessage,OffsetDateTime terminalizationRequestedAt){}
}
