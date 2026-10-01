package com.opensocket.aievent.core.integration.issue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Startup migration fallback for code-only Webhook reconciliation defaults. */
@Component
@ConfigurationProperties(prefix = "integration-sync")
public class ProviderWebhookReconciliationProperties {
    private long webhookReplayWindowSeconds = 300;
    private int webhookMaxAttempts = 5;
    private long webhookClaimLeaseSeconds = 60;
    private long webhookReconcileDelayMs = 60_000;

    public long getWebhookReplayWindowSeconds() { return webhookReplayWindowSeconds; }
    public void setWebhookReplayWindowSeconds(long value) { webhookReplayWindowSeconds = value; }
    public int getWebhookMaxAttempts() { return webhookMaxAttempts; }
    public void setWebhookMaxAttempts(int value) { webhookMaxAttempts = value; }
    public long getWebhookClaimLeaseSeconds() { return webhookClaimLeaseSeconds; }
    public void setWebhookClaimLeaseSeconds(long value) { webhookClaimLeaseSeconds = value; }
    public long getWebhookReconcileDelayMs() { return webhookReconcileDelayMs; }
    public void setWebhookReconcileDelayMs(long value) { webhookReconcileDelayMs = value; }
}
