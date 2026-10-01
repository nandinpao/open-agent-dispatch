package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/** Runtime-reschedulable scanner for delayed Task dispatch recovery. */
@Component
public class ScheduledTaskDispatchRecovery implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskDispatchRecovery.class);

    private final TaskOrchestrationFacade taskOrchestrationFacade;
    private final TaskDispatchRecoveryProperties properties;
    private final TaskDispatchRecoveryRuntimeConfigurationView runtimeConfigurationView;
    private final DynamicFixedDelayTask dynamicTask;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AtomicBoolean schemaWarningLogged = new AtomicBoolean(false);

    @org.springframework.beans.factory.annotation.Autowired
    public ScheduledTaskDispatchRecovery(TaskOrchestrationFacade taskOrchestrationFacade,
                                         TaskDispatchRecoveryProperties properties,
                                         TaskDispatchRecoveryRuntimeConfigurationView runtimeConfigurationView,
                                         JdbcTemplate jdbc,
                                         PlatformTransactionManager transactionManager,
                                         @Qualifier("dispatchOperationalScheduler") TaskScheduler scheduler) {
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.properties = properties;
        this.runtimeConfigurationView = runtimeConfigurationView;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "task-dispatch-recovery",
                this::recoverDelayedDispatches,
                runtimeConfigurationView::scanInterval);
    }

    // Source-compatible test constructor; Spring uses the @Autowired constructor above.
    ScheduledTaskDispatchRecovery(TaskOrchestrationFacade taskOrchestrationFacade,
                                  TaskDispatchRecoveryProperties properties,
                                  JdbcTemplate jdbc,
                                  PlatformTransactionManager transactionManager) {
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.properties = properties;
        this.runtimeConfigurationView = null;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.dynamicTask = null;
    }

    @Override public void afterPropertiesSet() { if (dynamicTask != null) dynamicTask.start(); }
    @Override public void destroy() { if (dynamicTask != null) dynamicTask.stop(); }

    public void recoverDelayedDispatches() {
        if (!runtimeEnabled() || !runtimeScannerEnabled()) {
            return;
        }
        List<String> tenants;
        try {
            tenants = jdbc.queryForList(
                    "select tenant_id from tenants where status='ACTIVE' order by tenant_id", String.class);
        } catch (RuntimeException failure) {
            log.error("task_dispatch_recovery_tenant_scan_failed workerId={} rootCause={}",
                    runtimeWorkerId(), rootMessage(failure), failure);
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
                        tenantId, runtimeWorkerId(), runtimeMaxBatchSize(), rootMessage(failure), failure);
            }
        }
        log.debug("task_dispatch_recovery_scan_completed workerId={} tenantCount={} processedTenants={} failedTenants={}",
                runtimeWorkerId(), tenants.size(), processedTenants, failedTenants);
    }

    private void recoverTenant(String tenantId) {
        String workerId = runtimeWorkerId();
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
        jdbc.queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, workerId);
        TaskDispatchRecoveryScanResult result = taskOrchestrationFacade.recoverDelayedDispatches(
                runtimeMaxBatchSize(), OffsetDateTime.now(ZoneOffset.UTC));
        if (result != null
                && result.getMessage() != null
                && result.getMessage().startsWith("Task dispatch recovery schema is not ready")
                && schemaWarningLogged.compareAndSet(false, true)) {
            log.warn("{}", result.getMessage());
        }
    }

    private boolean runtimeEnabled() {
        return runtimeConfigurationView == null ? properties.isEnabled() : runtimeConfigurationView.enabled();
    }

    private boolean runtimeScannerEnabled() {
        return runtimeConfigurationView == null ? properties.isScannerEnabled() : runtimeConfigurationView.scannerEnabled();
    }

    private int runtimeMaxBatchSize() {
        return runtimeConfigurationView == null ? properties.getMaxBatchSize() : runtimeConfigurationView.maxBatchSize();
    }

    private String runtimeWorkerId() {
        return runtimeConfigurationView == null ? properties.getWorkerId() : runtimeConfigurationView.workerId();
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
