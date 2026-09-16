package com.opensocket.aievent.core.integration.issue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(prefix="issue-tracking",name="enabled",havingValue="true",matchIfMissing=true)
public class ScheduledProviderWebhookReconciler {private final ProviderWebhookReconciliationService service;public ScheduledProviderWebhookReconciler(ProviderWebhookReconciliationService service){this.service=service;}@Scheduled(fixedDelayString="${integration-sync.webhook-reconcile-delay-ms:60000}")public void reconcile(){service.reconcileDue();}}
