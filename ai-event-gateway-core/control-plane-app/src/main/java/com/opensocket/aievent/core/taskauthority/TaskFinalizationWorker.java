package com.opensocket.aievent.core.taskauthority;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A0-R2 worker. Queue claim and each finalize/failure update use separate transactions. */
@Component
@ConditionalOnProperty(name="opendispatch.a0-r2.finalization.enabled",havingValue="true",matchIfMissing=true)
public class TaskFinalizationWorker {
    private final JdbcTemplate jdbc; private final TaskFinalizationQueueService queue; private final TaskFinalizationProcessor processor; private final int maxAttempts;
    public TaskFinalizationWorker(JdbcTemplate jdbc,TaskFinalizationQueueService queue,TaskFinalizationProcessor processor,
            @Value("${opendispatch.a0-r2.finalization.max-attempts:8}") int maxAttempts){this.jdbc=jdbc;this.queue=queue;this.processor=processor;this.maxAttempts=Math.max(1,Math.min(maxAttempts,50));}
    @Scheduled(fixedDelayString="${opendispatch.a0-r2.finalization.poll-ms:2000}", scheduler="maintenanceOperationalScheduler")
    public void run(){for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){for(var item:queue.claimDue(tenant)){try{processor.finalizeTask(tenant,item,queue.workerId());}catch(Exception ex){queue.fail(tenant,item,"TASK_FINALIZATION_FAILED",ex.getMessage(),maxAttempts);}}}}
}
