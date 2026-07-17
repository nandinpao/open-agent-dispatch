package com.opensocket.aievent.core.task;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "task.dispatch-recovery", name = "scanner-enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTaskDispatchRecovery {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskDispatchRecovery.class);

    private final TaskOrchestrationFacade taskOrchestrationFacade;
    private final TaskDispatchRecoveryProperties properties;
    private final AtomicBoolean schemaWarningLogged = new AtomicBoolean(false);

    public ScheduledTaskDispatchRecovery(TaskOrchestrationFacade taskOrchestrationFacade,
                                         TaskDispatchRecoveryProperties properties) {
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${task.dispatch-recovery.interval-ms:5000}")
    public void recoverDelayedDispatches() {
        if (!properties.isEnabled() || !properties.isScannerEnabled()) {
            return;
        }
        TaskDispatchRecoveryScanResult result = taskOrchestrationFacade.recoverDelayedDispatches(
                properties.getMaxBatchSize(),
                OffsetDateTime.now(ZoneOffset.UTC));
        if (result != null
                && result.getMessage() != null
                && result.getMessage().startsWith("Task dispatch recovery schema is not ready")
                && schemaWarningLogged.compareAndSet(false, true)) {
            log.warn("{}", result.getMessage());
        }
    }
}
