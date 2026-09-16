package com.opensocket.aievent.core.analytics;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded asynchronous Phase 12.6 projection worker. OLTP only enqueues projection events. */
@Component
@ConditionalOnProperty(name="opendispatch.analytics.projection.enabled",havingValue="true",matchIfMissing=true)
public class EnterpriseAnalyticsProjectionWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final int rollupBatchSize;

    public EnterpriseAnalyticsProjectionWorker(JdbcTemplate jdbc,PlatformTransactionManager transactionManager,
            @Value("${opendispatch.analytics.projection.batch-size:250}") int batchSize,
            @Value("${opendispatch.analytics.rollup.batch-size:48}") int rollupBatchSize) {
        this.jdbc=jdbc; this.transactions=new TransactionTemplate(transactionManager); this.batchSize=Math.max(1,Math.min(batchSize,1000));
        this.rollupBatchSize=Math.max(1,Math.min(rollupBatchSize,500));
    }

    @Scheduled(fixedDelayString="${opendispatch.analytics.projection.poll-ms:5000}", scheduler="projectionOperationalScheduler")
    public void projectPending() {
        List<String> tenantIds=jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class);
        for(String tenantId:tenantIds) {
            transactions.executeWithoutResult(status->{
                jdbc.queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenantId);
                jdbc.queryForObject("select phase12_6_project_pending(?,?)",Integer.class,tenantId,batchSize);
                jdbc.queryForObject("select phase12_7_refresh_dirty_rollups(?,?)",Integer.class,tenantId,rollupBatchSize);
            });
        }
    }
}
