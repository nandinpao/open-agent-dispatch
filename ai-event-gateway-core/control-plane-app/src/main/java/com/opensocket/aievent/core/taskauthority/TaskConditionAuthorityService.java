package com.opensocket.aievent.core.taskauthority;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A0-R2 authoritative TaskCondition lifecycle and timeout-to-terminalization bridge. */
@Service
public class TaskConditionAuthorityService {
    private final JdbcTemplate jdbc;
    public TaskConditionAuthorityService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public void raise(String tenant,String taskId,String condition,String reasonCode,String reason,String sourceRef,String actor){bind(tenant,actor);Policy p=policy(tenant,condition);OffsetDateTime timeout=p.timeoutSeconds()==null?null:OffsetDateTime.now().plusSeconds(p.timeoutSeconds());jdbc.update("""
      insert into task_conditions(tenant_id,task_id,condition_type,blocking,resolution,timeout_at,timeout_action,timeout_terminalization_reason,reason_code,reason,source_ref,raised_by)
      values(?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,task_id,condition_type) do update set condition_state='ACTIVE',blocking=excluded.blocking,resolution=excluded.resolution,
      timeout_at=excluded.timeout_at,timeout_action=excluded.timeout_action,timeout_terminalization_reason=excluded.timeout_terminalization_reason,reason_code=excluded.reason_code,reason=excluded.reason,source_ref=excluded.source_ref,raised_by=excluded.raised_by,raised_at=now(),resolved_by=null,resolved_at=null,resolution_note=null,version=task_conditions.version+1
      """,tenant,taskId,condition,p.blocking(),p.resolution(),timeout,p.timeoutAction(),p.terminalReason(),reasonCode,reason,sourceRef,actor);if(p.blocking())jdbc.update("update tasks set task_lifecycle='WAITING',updated_at=now() where tenant_id=? and task_id=? and task_lifecycle in ('CREATED','ACTIVE')",tenant,taskId);if("FAIL".equals(p.resolution())&&Long.valueOf(0L).equals(p.timeoutSeconds()))terminalize(tenant,taskId,p.terminalReason(),actor,reason);}

    @Transactional
    public void resolve(String tenant,String taskId,String condition,String actor,String note){bind(tenant,actor);jdbc.update("update task_conditions set condition_state='RESOLVED',resolved_by=?,resolved_at=now(),resolution_note=?,version=version+1 where tenant_id=? and task_id=? and condition_type=? and condition_state='ACTIVE'",actor,note,tenant,taskId,condition);Integer blockers=jdbc.queryForObject("select count(*) from task_conditions where tenant_id=? and task_id=? and condition_state='ACTIVE' and blocking=true",Integer.class,tenant,taskId);if(blockers!=null&&blockers==0)jdbc.update("update tasks set task_lifecycle='ACTIVE',updated_at=now() where tenant_id=? and task_id=? and task_lifecycle='WAITING'",tenant,taskId);}

    @Transactional
    public int processDue(String tenant,String actor,int limit){bind(tenant,actor);List<Due> due=jdbc.query("select task_id,condition_type,timeout_action,timeout_terminalization_reason,reason from task_conditions where tenant_id=? and condition_state='ACTIVE' and timeout_at is not null and timeout_at<=now() order by timeout_at,task_id limit ? for update skip locked",(rs,n)->new Due(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),tenant,Math.max(1,Math.min(limit,500)));for(Due d:due){if("FAIL".equals(d.action()))terminalize(tenant,d.taskId(),d.terminalReason(),actor,d.reason());else if("MANUAL_RECONCILIATION".equals(d.action()))raiseManualRecovery(tenant,d,actor);jdbc.update("update task_conditions set condition_state='EXPIRED',resolved_by=?,resolved_at=now(),resolution_note='Condition timeout processed',version=version+1 where tenant_id=? and task_id=? and condition_type=? and condition_state='ACTIVE'",actor,tenant,d.taskId(),d.condition());}return due.size();}

    private void terminalize(String tenant,String task,String reason,String actor,String explanation){if(reason==null||reason.isBlank())reason="RUNTIME_FATAL";int n=jdbc.update("""
      update tasks set task_lifecycle='FINALIZING',task_phase='CLOSURE',task_outcome='UNRESOLVED',terminalization_reason=?,terminalization_requested_at=now(),terminalization_actor_ref=?,finalization_state='PENDING',finalization_checkpoint='NOT_STARTED',finalization_next_attempt_at=now(),updated_at=now(),lifecycle_reason=coalesce(?,lifecycle_reason)
      where tenant_id=? and task_id=? and task_lifecycle in ('CREATED','ACTIVE','WAITING')
      """,reason,actor,explanation,tenant,task);if(n==1)jdbc.update("insert into task_terminalization_events(tenant_id,event_id,task_id,terminalization_reason,actor_ref,reason,occurred_at) values(?,concat('term-',md5(?||':'||clock_timestamp()::text||':'||random()::text)),?,?,?,?,now())",tenant,tenant+":"+task,task,reason,actor,explanation);}
    private void raiseManualRecovery(String tenant,Due d,String actor){jdbc.update("insert into task_conditions(tenant_id,task_id,condition_type,blocking,resolution,timeout_action,reason_code,reason,raised_by) values(?,?,'MANUAL_RECOVERY_REQUIRED',false,'REQUIRE_HUMAN','NONE','CONDITION_TIMEOUT_RECONCILIATION',?,?) on conflict(tenant_id,task_id,condition_type) do update set condition_state='ACTIVE',reason=excluded.reason,raised_by=excluded.raised_by,raised_at=now(),version=task_conditions.version+1",tenant,d.taskId(),d.reason(),actor);}
    private Policy policy(String tenant,String condition){List<Policy> rows=jdbc.query("select blocking,resolution,timeout_seconds,timeout_action,timeout_terminalization_reason from task_condition_resolution_policies where status='ACTIVE' and condition_type=? and ((scope_type='TENANT' and scope_id=?) or (scope_type='SYSTEM' and scope_id='*')) order by case when scope_type='TENANT' then 0 else 1 end limit 1",(rs,n)->new Policy(rs.getBoolean(1),rs.getString(2),(Long)rs.getObject(3),rs.getString(4),rs.getString(5)),condition,tenant);if(rows.isEmpty())throw new IllegalArgumentException("TASK_CONDITION_POLICY_NOT_FOUND:"+condition);return rows.getFirst();}
    private void bind(String tenant,String actor){jdbc.queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenant);jdbc.queryForObject("select set_config('app.current_actor_id',?,true)",String.class,actor);}
    private record Policy(boolean blocking,String resolution,Long timeoutSeconds,String timeoutAction,String terminalReason){} private record Due(String taskId,String condition,String action,String terminalReason,String reason){}
}
