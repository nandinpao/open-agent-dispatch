package com.opensocket.aievent.core.integration.issue;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class ProviderWebhookReconciliationRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private static final Set<String> KEYS = Set.of(
            ProviderWebhookReconciliationRuntimeConfigurationView.REPLAY_WINDOW_SECONDS,
            ProviderWebhookReconciliationRuntimeConfigurationView.MAX_ATTEMPTS,
            ProviderWebhookReconciliationRuntimeConfigurationView.CLAIM_LEASE_SECONDS,
            ProviderWebhookReconciliationRuntimeConfigurationView.RECONCILE_DELAY_MS);
    private final ProviderWebhookReconciliationRuntimeConfigurationView view;

    public ProviderWebhookReconciliationRuntimeConfigurationEffectiveValueResolver(ProviderWebhookReconciliationRuntimeConfigurationView view) { this.view = view; }
    @Override public String owner() { return "INTEGRATION_SYNC_WEBHOOK_RECONCILIATION"; }
    @Override public Set<String> supportedKeys() { return KEYS; }
    @Override public Object resolve(String key) {
        return switch (key) {
            case ProviderWebhookReconciliationRuntimeConfigurationView.REPLAY_WINDOW_SECONDS -> view.replayWindowSeconds();
            case ProviderWebhookReconciliationRuntimeConfigurationView.MAX_ATTEMPTS -> view.maxAttempts();
            case ProviderWebhookReconciliationRuntimeConfigurationView.CLAIM_LEASE_SECONDS -> view.claimLeaseSeconds();
            case ProviderWebhookReconciliationRuntimeConfigurationView.RECONCILE_DELAY_MS -> view.reconcileDelay().toMillis();
            default -> throw new IllegalArgumentException("WEBHOOK_RECONCILIATION_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
