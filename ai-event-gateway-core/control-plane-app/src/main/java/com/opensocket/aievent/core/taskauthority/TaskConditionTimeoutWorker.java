package com.opensocket.aievent.core.taskauthority;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.taskauthority.runtime.TaskAuthorityRuntimeConfigurationView;

@Component
public class TaskConditionTimeoutWorker {
    private final JdbcTemplate jdbc; private final TaskConditionAuthorityService conditions; private final TaskAuthorityRuntimeConfigurationView runtime;
    public TaskConditionTimeoutWorker(JdbcTemplate jdbc,TaskConditionAuthorityService conditions,TaskAuthorityRuntimeConfigurationView runtime){this.jdbc=jdbc;this.conditions=conditions;this.runtime=runtime;}
    public void run(){if(!runtime.conditionsEnabled())return;int batch=runtime.conditionsBatchSize();for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){conditions.processDue(tenant,"a0-r2-condition-worker",batch);}}
}
