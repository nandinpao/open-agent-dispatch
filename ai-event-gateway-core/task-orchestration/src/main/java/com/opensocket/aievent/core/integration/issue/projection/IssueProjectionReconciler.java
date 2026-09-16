package com.opensocket.aievent.core.integration.issue.projection;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix="issue-tracking",name="enabled",havingValue="true",matchIfMissing=true)
public class IssueProjectionReconciler {
    private final IssueProjectionStateService service;
    public IssueProjectionReconciler(IssueProjectionStateService service) { this.service = service; }
    @Scheduled(fixedDelayString = "${issue-projection.reconcile-delay-ms:60000}", scheduler = "projectionOperationalScheduler")
    public void reconcile() { service.reconcileDue(); }
}
