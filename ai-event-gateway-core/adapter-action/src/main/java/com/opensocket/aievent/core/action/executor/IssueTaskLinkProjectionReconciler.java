package com.opensocket.aievent.core.action.executor;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

/**
 * Projection-only recovery loop for durable Issue provider results.
 * This component never calls a provider connector; it only asks the execution service
 * to rebuild TaskIssueLink from already-observed provider evidence.
 */
@Component
public class IssueTaskLinkProjectionReconciler implements InitializingBean, DisposableBean {
    private final AdapterActionExecutionService service;
    private final DynamicFixedDelayTask dynamicTask;
    private final AdapterExecutorRuntimeConfigurationView runtimeConfiguration;

    public IssueTaskLinkProjectionReconciler(
            AdapterActionExecutionService service,
            AdapterExecutorRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("projectionOperationalScheduler") TaskScheduler scheduler) {
        this.service = service;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "issue-task-link-projection-reconciler",
                this::reconcile,
                runtimeConfiguration::issueLinkProjectionReconciliationDelay);
    }

    @Override public void afterPropertiesSet() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }

    public void reconcile() {
        if (!runtimeConfiguration.issueLinkProjectionReconciliationEnabled()) return;
        service.reconcileIssueLinkProjections(runtimeConfiguration.issueLinkProjectionBatchSize());
    }
}
