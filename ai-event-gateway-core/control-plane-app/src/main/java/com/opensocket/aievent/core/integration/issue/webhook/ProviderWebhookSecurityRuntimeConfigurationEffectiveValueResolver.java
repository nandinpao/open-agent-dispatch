package com.opensocket.aievent.core.integration.issue.webhook;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class ProviderWebhookSecurityRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private static final Set<String> KEYS = Set.of(
            ProviderWebhookSecurityRuntimeConfigurationView.ENABLED,
            ProviderWebhookSecurityRuntimeConfigurationView.DEFAULT_MAX_BODY_BYTES,
            ProviderWebhookSecurityRuntimeConfigurationView.DEFAULT_RATE_LIMIT_PER_MINUTE,
            ProviderWebhookSecurityRuntimeConfigurationView.CLOCK_SKEW_SECONDS,
            ProviderWebhookSecurityRuntimeConfigurationView.NONCE_CLEANUP_INTERVAL_MS,
            ProviderWebhookSecurityRuntimeConfigurationView.NONCE_CLEANUP_BATCH_SIZE);
    private final ProviderWebhookSecurityRuntimeConfigurationView view;

    public ProviderWebhookSecurityRuntimeConfigurationEffectiveValueResolver(ProviderWebhookSecurityRuntimeConfigurationView view) { this.view = view; }
    @Override public String owner() { return "INTEGRATION_SYNC_WEBHOOK_SECURITY"; }
    @Override public Set<String> supportedKeys() { return KEYS; }
    @Override public Object resolve(String key) {
        return switch (key) {
            case ProviderWebhookSecurityRuntimeConfigurationView.ENABLED -> view.enabled();
            case ProviderWebhookSecurityRuntimeConfigurationView.DEFAULT_MAX_BODY_BYTES -> view.defaultMaxBodyBytes();
            case ProviderWebhookSecurityRuntimeConfigurationView.DEFAULT_RATE_LIMIT_PER_MINUTE -> view.defaultRateLimitPerMinute();
            case ProviderWebhookSecurityRuntimeConfigurationView.CLOCK_SKEW_SECONDS -> view.clockSkewSeconds();
            case ProviderWebhookSecurityRuntimeConfigurationView.NONCE_CLEANUP_INTERVAL_MS -> view.nonceCleanupInterval().toMillis();
            case ProviderWebhookSecurityRuntimeConfigurationView.NONCE_CLEANUP_BATCH_SIZE -> view.nonceCleanupBatchSize();
            default -> throw new IllegalArgumentException("WEBHOOK_SECURITY_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
