package com.opensocket.aievent.core.action.executor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Projection-only recovery loop for durable Issue provider results.
 * This component never calls a provider connector; it only asks the execution service
 * to rebuild TaskIssueLink from already-observed provider evidence.
 */
@Component
public class IssueTaskLinkProjectionReconciler {
    private final AdapterActionExecutionService service;
    private final AdapterActionExecutionProperties properties;

    public IssueTaskLinkProjectionReconciler(AdapterActionExecutionService service,
                                             AdapterActionExecutionProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${adapter-executor.issue.link-projection-reconciliation-delay:30s}", scheduler = "projectionOperationalScheduler")
    public void reconcile() {
        if (!properties.getIssue().isLinkProjectionReconciliationEnabled()) return;
        service.reconcileIssueLinkProjections(properties.getIssue().getLinkProjectionBatchSize());
    }
}
