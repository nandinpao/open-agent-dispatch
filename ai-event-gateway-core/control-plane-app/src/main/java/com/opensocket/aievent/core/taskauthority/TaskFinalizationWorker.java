package com.opensocket.aievent.core.taskauthority;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.taskauthority.runtime.TaskAuthorityRuntimeConfigurationView;

/** A0-R2 worker. Queue claim and each finalize/failure update use separate transactions. */
@Component
public class TaskFinalizationWorker {
    private final JdbcTemplate jdbc; private final TaskFinalizationQueueService queue; private final TaskFinalizationProcessor processor; private final TaskAuthorityRuntimeConfigurationView runtime;
    public TaskFinalizationWorker(JdbcTemplate jdbc,TaskFinalizationQueueService queue,TaskFinalizationProcessor processor,TaskAuthorityRuntimeConfigurationView runtime){this.jdbc=jdbc;this.queue=queue;this.processor=processor;this.runtime=runtime;}
    public void run(){if(!runtime.finalizationEnabled())return;int maxAttempts=runtime.finalizationMaxAttempts();for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){for(var item:queue.claimDue(tenant)){try{processor.finalizeTask(tenant,item,queue.workerId());}catch(Exception ex){queue.fail(tenant,item,"TASK_FINALIZATION_FAILED",ex.getMessage(),maxAttempts);}}}}
}
