package com.opensocket.aievent.core.integration.issue;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;

@Component
@ConditionalOnProperty(prefix="issue-tracking",name="enabled",havingValue="true",matchIfMissing=true)
public class ScheduledProviderWebhookReconciler implements InitializingBean, DisposableBean {
    private final ProviderWebhookReconciliationService service;
    private final DynamicFixedDelayTask dynamicTask;

    public ScheduledProviderWebhookReconciler(
            ProviderWebhookReconciliationService service,
            ProviderWebhookReconciliationRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("projectionOperationalScheduler") TaskScheduler scheduler) {
        this.service = service;
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "provider-webhook-reconciler",
                this::reconcile,
                runtimeConfiguration::reconcileDelay);
    }

    @Override public void afterPropertiesSet() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }
    public void reconcile() { service.reconcileDue(); }
}
