package com.opensocket.aievent.core.analytics;

import java.util.List;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;import com.opensocket.aievent.core.analytics.runtime.AnalyticsRuntimeConfigurationView;

/** Bounded asynchronous analytics projection worker. */
@Component
public class EnterpriseAnalyticsProjectionWorker {
    private final JdbcTemplate jdbc; private final TransactionTemplate transactions; private final AnalyticsRuntimeConfigurationView runtime;
    public EnterpriseAnalyticsProjectionWorker(JdbcTemplate jdbc,PlatformTransactionManager transactionManager,AnalyticsRuntimeConfigurationView runtime){this.jdbc=jdbc;this.transactions=new TransactionTemplate(transactionManager);this.runtime=runtime;}
    public void projectPending(){if(!runtime.enabled())return;int batchSize=runtime.batchSize();int rollupBatchSize=runtime.rollupBatchSize();List<String> tenantIds=jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class);for(String tenantId:tenantIds){transactions.executeWithoutResult(status->{jdbc.queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenantId);jdbc.queryForObject("select phase12_6_project_pending(?,?)",Integer.class,tenantId,batchSize);jdbc.queryForObject("select phase12_7_refresh_dirty_rollups(?,?)",Integer.class,tenantId,rollupBatchSize);});}}
}
