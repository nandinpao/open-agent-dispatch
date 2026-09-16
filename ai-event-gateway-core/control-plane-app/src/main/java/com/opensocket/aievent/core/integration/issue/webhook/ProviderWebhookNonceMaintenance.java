package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime; import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.integration.identity.IntegrationWebhookEndpointRepository;
@Component @ConditionalOnProperty(prefix="integration-sync.webhook-security",name="enabled",havingValue="true",matchIfMissing=true)
public class ProviderWebhookNonceMaintenance {
 private final IntegrationWebhookEndpointRepository repository; private final ProviderWebhookSecurityProperties properties;
 public ProviderWebhookNonceMaintenance(IntegrationWebhookEndpointRepository repository,ProviderWebhookSecurityProperties properties){this.repository=repository;this.properties=properties;}
 @Scheduled(fixedDelayString="${integration-sync.webhook-security.nonce-cleanup-interval-ms:60000}", scheduler="maintenanceOperationalScheduler") public void cleanupExpiredNonces(){repository.deleteExpiredNonces(OffsetDateTime.now(ZoneOffset.UTC),Math.max(1,properties.getNonceCleanupBatchSize()));}
}
