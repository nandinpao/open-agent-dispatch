package com.opensocket.aievent.core.integration.handoff;

import org.springframework.scheduling.annotation.Scheduled; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Periodically repairs approved Handoff Snapshots whose dispatch release is incomplete. */
@ConditionalOnProperty(prefix="a2a.legacy-reconcilers",name="handoff-enabled",havingValue="true",matchIfMissing=false)
public final class ScheduledHandoffContextReconciler {
    private final HandoffContextService service;

    public ScheduledHandoffContextReconciler(HandoffContextService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${handoff-context.reconciliation-ms:30000}", scheduler = "reconciliationOperationalScheduler")
    public void reconcile() {
        service.reconcileDue(100);
    }
}
