package com.opensocket.aievent.core.taskauthority;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="opendispatch.a0-r2.conditions.enabled",havingValue="true",matchIfMissing=true)
public class TaskConditionTimeoutWorker {
    private final JdbcTemplate jdbc; private final TaskConditionAuthorityService conditions; private final int batch;
    public TaskConditionTimeoutWorker(JdbcTemplate jdbc,TaskConditionAuthorityService conditions,@Value("${opendispatch.a0-r2.conditions.batch-size:100}") int batch){this.jdbc=jdbc;this.conditions=conditions;this.batch=Math.max(1,Math.min(batch,500));}
    @Scheduled(fixedDelayString="${opendispatch.a0-r2.conditions.poll-ms:5000}", scheduler="maintenanceOperationalScheduler") public void run(){for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){conditions.processDue(tenant,"a0-r2-condition-worker",batch);}}
}
