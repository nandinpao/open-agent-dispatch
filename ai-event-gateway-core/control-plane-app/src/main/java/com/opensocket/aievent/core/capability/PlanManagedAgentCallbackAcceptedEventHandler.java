package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bridges canonical Managed-Agent callback evidence into the Stage 9 Plan Step convergence path. */
@Component
public class PlanManagedAgentCallbackAcceptedEventHandler implements ModuleEventHandler<TaskCallbackAcceptedEvent> {
    private final NamedParameterJdbcTemplate jdbc;
    private final GovernedPlanExecutionService plans;
    public PlanManagedAgentCallbackAcceptedEventHandler(NamedParameterJdbcTemplate jdbc,GovernedPlanExecutionService plans){this.jdbc=jdbc;this.plans=plans;}
    @Override public String eventType(){return TaskCallbackAcceptedEvent.TYPE;}
    @Override public Class<TaskCallbackAcceptedEvent> payloadType(){return TaskCallbackAcceptedEvent.class;}

    @Override
    @Transactional
    public void handle(TaskCallbackAcceptedEvent event){
        if(event==null||!("RESULT".equalsIgnoreCase(event.callbackType())||"ERROR".equalsIgnoreCase(event.callbackType())))return;
        Attempt a=find(event.tenantId(),event.taskId());if(a==null)return;
        boolean success=!"ERROR".equalsIgnoreCase(event.callbackType())&&!"FAILED".equalsIgnoreCase(event.taskStatus())&&!"FAILED".equalsIgnoreCase(event.resultStatus());
        Map<String,Object> output=event.payload()==null?Map.of():event.payload();
        plans.recordRuntimeCompletion(event.tenantId(),a.runId(),a.stepId(),new PlanRuntimeStepCompletion(
                a.externalExecutionRef(),success?"SUCCEEDED":"FAILED",event.message(),null,
                List.of("callback:"+event.callbackId(),"assignment:"+safe(event.assignmentId()),"agent:"+safe(event.agentId())),
                output,null,success?null:(event.errorCode()==null?"MANAGED_AGENT_CALLBACK_FAILED":event.errorCode())));
    }
    private Attempt find(String tenant,String task){
        bind(tenant);try{return jdbc.queryForObject("""
          select a.run_id,a.step_id,a.external_execution_ref
            from plan_execution_attempts a
           where a.tenant_id=:tenant and a.child_task_ref=:task and a.state in ('SUBMITTED','RUNNING','CANCEL_REQUESTED')
           order by a.attempt_no desc limit 1
          """,new MapSqlParameterSource("tenant",tenant).addValue("task",task),(rs,n)->new Attempt(rs.getString(1),rs.getString(2),rs.getString(3)));}
        catch(EmptyResultDataAccessException ex){return null;}
    }
    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"plan-managed-callback");}
    private static String safe(String s){return s==null?"":s;}
    private record Attempt(String runId,String stepId,String externalExecutionRef){}
}
