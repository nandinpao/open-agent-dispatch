package com.opensocket.aievent.core.action;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAdapterWorkerLeaseRecovery {
    private final AdapterActionService service;
    private final AdapterActionProperties properties;

    public ScheduledAdapterWorkerLeaseRecovery(AdapterActionService service, AdapterActionProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${adapter-actions.worker.expired-lease-scan-interval-ms:30000}")
    public void recoverExpiredLeases() {
        service.recoverExpiredWorkerLeases(properties.getWorker().getExpiredLeaseScanBatchSize());
    }
}
