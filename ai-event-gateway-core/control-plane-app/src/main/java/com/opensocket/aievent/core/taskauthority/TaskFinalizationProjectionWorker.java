package com.opensocket.aievent.core.taskauthority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="opendispatch.a0-r2.finalization.projection-enabled",havingValue="true",matchIfMissing=true)
public class TaskFinalizationProjectionWorker {
    private static final Logger log = LoggerFactory.getLogger(TaskFinalizationProjectionWorker.class);
    private final JdbcTemplate jdbc; private final TaskFinalizationProjectionService service; private final int batch;
    public TaskFinalizationProjectionWorker(JdbcTemplate jdbc,TaskFinalizationProjectionService service,@Value("${opendispatch.a0-r2.finalization.projection-batch-size:50}") int batch){this.jdbc=jdbc;this.service=service;this.batch=Math.max(1,Math.min(batch,250));}

    @Scheduled(fixedDelayString="${opendispatch.a0-r2.finalization.projection-poll-ms:3000}", scheduler="projectionOperationalScheduler")
    public void run(){
        for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){
            for(int i=0;i<batch;i++){
                try {
                    if(!service.projectOne(tenant,"a0-r2-finalization-projection")) break;
                } catch(Exception ex) {
                    // A single poison row or transient bookkeeping error must not abort the tenant batch.
                    log.error("task_finalization_issue_projection_worker_item_failed tenantId={} batchIndex={} error={}",
                            tenant,i,root(ex));
                }
            }
        }
    }
    private static String root(Throwable t){Throwable c=t;while(c.getCause()!=null)c=c.getCause();return c.getMessage()==null?c.getClass().getSimpleName():c.getMessage();}
}
