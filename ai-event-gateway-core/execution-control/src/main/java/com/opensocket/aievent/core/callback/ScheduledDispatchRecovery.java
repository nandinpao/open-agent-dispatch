package com.opensocket.aievent.core.callback;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "task.callback.recovery", name = "timeout-enabled", havingValue = "true")
public class ScheduledDispatchRecovery {
    private final DispatchRecoveryService recoveryService;
    private final TaskCallbackProperties properties;

    public ScheduledDispatchRecovery(DispatchRecoveryService recoveryService, TaskCallbackProperties properties) {
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${task.callback.recovery.scan-interval-ms:30000}")
    public void recoverTimedOutDispatches() {
        recoveryService.scanAndRecoverTimedOut(properties.getRecovery().getMaxBatchSize());
    }
}
