package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "task.dispatch-recovery", name = "scanner-enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTaskDispatchRecovery {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskDispatchRecovery.class);

    private final TaskOrchestrationFacade taskOrchestrationFacade;
    private final TaskDispatchRecoveryProperties properties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AtomicBoolean schemaWarningLogged = new AtomicBoolean(false);

    public ScheduledTaskDispatchRecovery(TaskOrchestrationFacade taskOrchestrationFacade,
                                         TaskDispatchRecoveryProperties properties,
                                         JdbcTemplate jdbc,
                                         PlatformTransactionManager transactionManager) {
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.properties = properties;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${task.dispatch-recovery.interval-ms:5000}", scheduler = "dispatchOperationalScheduler")
    public void recoverDelayedDispatches() {
        if (!properties.isEnabled() || !properties.isScannerEnabled()) {
            return;
        }
        List<String> tenants;
        try {
            tenants = jdbc.queryForList(
                    "select tenant_id from tenants where status='ACTIVE' order by tenant_id", String.class);
        } catch (RuntimeException failure) {
            log.error("task_dispatch_recovery_tenant_scan_failed workerId={} rootCause={}",
                    properties.getWorkerId(), rootMessage(failure), failure);
            return;
        }

        int processedTenants = 0;
        int failedTenants = 0;
        for (String tenantId : tenants) {
            if (tenantId == null || tenantId.isBlank()) continue;
            try {
                transactions.executeWithoutResult(status -> recoverTenant(tenantId));
                processedTenants++;
            } catch (RuntimeException failure) {
                failedTenants++;
                log.error("task_dispatch_recovery_tenant_failed tenantId={} workerId={} maxBatchSize={} rootCause={}",
                        tenantId, properties.getWorkerId(), properties.getMaxBatchSize(), rootMessage(failure), failure);
            }
        }
        log.debug("task_dispatch_recovery_scan_completed workerId={} tenantCount={} processedTenants={} failedTenants={}",
                properties.getWorkerId(), tenants.size(), processedTenants, failedTenants);
    }

    private void recoverTenant(String tenantId) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, properties.getWorkerId());
        TaskDispatchRecoveryScanResult result = taskOrchestrationFacade.recoverDelayedDispatches(
                properties.getMaxBatchSize(), OffsetDateTime.now(ZoneOffset.UTC));
        if (result != null
                && result.getMessage() != null
                && result.getMessage().startsWith("Task dispatch recovery schema is not ready")
                && schemaWarningLogged.compareAndSet(false, true)) {
            log.warn("{}", result.getMessage());
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        String message = failure == null ? "unknown" : failure.toString();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) message = current.getMessage();
            current = current.getCause();
        }
        return message;
    }
}
