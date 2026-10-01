package com.opensocket.aievent.core.integration.issue.webhook;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;
import com.opensocket.aievent.core.integration.identity.IntegrationWebhookEndpointRepository;

@Component
public class ProviderWebhookNonceMaintenance implements InitializingBean, DisposableBean {
    private final IntegrationWebhookEndpointRepository repository;
    private final ProviderWebhookSecurityRuntimeConfigurationView runtimeConfiguration;
    private final DynamicFixedDelayTask dynamicTask;

    public ProviderWebhookNonceMaintenance(
            IntegrationWebhookEndpointRepository repository,
            ProviderWebhookSecurityRuntimeConfigurationView runtimeConfiguration,
            @Qualifier("maintenanceOperationalScheduler") TaskScheduler scheduler) {
        this.repository = repository;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new DynamicFixedDelayTask(
                scheduler,
                "provider-webhook-nonce-maintenance",
                this::cleanupExpiredNonces,
                runtimeConfiguration::nonceCleanupInterval);
    }

    @Override public void afterPropertiesSet() { dynamicTask.start(); }
    @Override public void destroy() { dynamicTask.stop(); }

    public void cleanupExpiredNonces() {
        if (!runtimeConfiguration.enabled()) return;
        repository.deleteExpiredNonces(
                OffsetDateTime.now(ZoneOffset.UTC),
                runtimeConfiguration.nonceCleanupBatchSize());
    }
}
