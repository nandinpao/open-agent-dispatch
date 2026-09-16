package com.opensocket.aievent.core.dispatch.flow;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Automatic A0-R4 shadow evaluator. It consumes only migration work; it never dispatches execution. */
@Component
public class FlowCapabilityLegacyEquivalenceWorker {
    private final FlowCapabilityLegacyEquivalenceService service;
    private final JdbcTemplate jdbc;
    private final int batchSize;
    private final String workerId = "a0-r4-shadow-" + UUID.randomUUID();

    public FlowCapabilityLegacyEquivalenceWorker(FlowCapabilityLegacyEquivalenceService service, JdbcTemplate jdbc,
            @Value("${opendispatch.a0-r4.shadow.batch-size:25}") int batchSize) {
        this.service=service;this.jdbc=jdbc;this.batchSize=Math.max(1,Math.min(batchSize,200));
    }

    @Scheduled(fixedDelayString="${opendispatch.a0-r4.shadow.poll-ms:3000}")
    public void run(){
        for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){
            for(var item:service.claimDue(tenant,workerId,batchSize)){
                try{service.evaluateTask(tenant,item.flowId(),item.taskId(),item.assignmentId(),workerId);service.complete(tenant,item.workItemId());}
                catch(Exception ex){service.fail(tenant,item.workItemId(),item.attemptCount(),ex.getMessage());}
            }
        }
    }
}
