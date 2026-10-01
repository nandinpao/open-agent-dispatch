package com.opensocket.aievent.core.taskauthority;

import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;import com.opensocket.aievent.core.taskauthority.runtime.TaskAuthorityRuntimeConfigurationView;

@Component
public class TaskFinalizationProjectionWorker {
    private static final Logger log=LoggerFactory.getLogger(TaskFinalizationProjectionWorker.class);
    private final JdbcTemplate jdbc; private final TaskFinalizationProjectionService service; private final TaskAuthorityRuntimeConfigurationView runtime;
    public TaskFinalizationProjectionWorker(JdbcTemplate jdbc,TaskFinalizationProjectionService service,TaskAuthorityRuntimeConfigurationView runtime){this.jdbc=jdbc;this.service=service;this.runtime=runtime;}
    public void run(){if(!runtime.projectionEnabled())return;int batch=runtime.projectionBatchSize();for(String tenant:jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class)){for(int i=0;i<batch;i++){try{if(!service.projectOne(tenant,"task-finalization-projection-worker"))break;}catch(Exception ex){log.error("task_finalization_issue_projection_worker_item_failed tenantId={} batchIndex={} error={}",tenant,i,ex.toString(),ex);break;}}}}
}
