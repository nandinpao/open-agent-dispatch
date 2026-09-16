package com.opensocket.aievent.core.enforcement.activation.runtime;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import com.opensocket.aievent.core.enforcement.activation.application.AuthorityRevisionSynchronizationService;

public final class ScheduledAuthorityRevisionSynchronizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledAuthorityRevisionSynchronizer.class);
    private final AuthorityRevisionSynchronizationService synchronization;
    private final String nodeId;

    public ScheduledAuthorityRevisionSynchronizer(
            AuthorityRevisionSynchronizationService synchronization,
            String nodeId) {
        this.synchronization = synchronization;
        this.nodeId = nodeId;
    }

    @Scheduled(
            fixedDelayString = "${aeg.enforcement-activation.revision-sync-interval-ms:2000}",
            initialDelayString = "${aeg.enforcement-activation.revision-sync-initial-delay-ms:3000}", scheduler = "maintenanceOperationalScheduler")
    public void synchronize() {
        String correlation = "phase6c0-sync-" + nodeId + "-" + UUID.randomUUID();
        try {
            synchronization.synchronize("system:phase6c0-sync:" + nodeId, correlation);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Phase 6C-0 cluster revision synchronization failed. nodeId={} correlationId={}",
                    nodeId,
                    correlation,
                    exception);
        }
    }
}
