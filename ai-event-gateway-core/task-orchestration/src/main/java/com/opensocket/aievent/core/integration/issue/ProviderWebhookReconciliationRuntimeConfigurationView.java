package com.opensocket.aievent.core.integration.issue;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** Typed local-snapshot view for Provider Webhook reconciliation execution policy. */
@Component
public final class ProviderWebhookReconciliationRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.INTEGRATION_SYNC_SYSTEM;
    public static final String REPLAY_WINDOW_SECONDS = "integration-sync.webhook-replay-window-seconds";
    public static final String MAX_ATTEMPTS = "integration-sync.webhook-max-attempts";
    public static final String CLAIM_LEASE_SECONDS = "integration-sync.webhook-claim-lease-seconds";
    public static final String RECONCILE_DELAY_MS = "integration-sync.webhook-reconcile-delay-ms";

    private final ProviderWebhookReconciliationProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public ProviderWebhookReconciliationRuntimeConfigurationView(
            ProviderWebhookReconciliationProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    public long replayWindowSeconds() {
        long value = longValue(REPLAY_WINDOW_SECONDS, startup.getWebhookReplayWindowSeconds());
        if (value < 30 || value > 86_400) throw invalid(REPLAY_WINDOW_SECONDS);
        return value;
    }

    public int maxAttempts() {
        int value = integerValue(MAX_ATTEMPTS, startup.getWebhookMaxAttempts());
        if (value < 1 || value > 50) throw invalid(MAX_ATTEMPTS);
        return value;
    }

    public long claimLeaseSeconds() {
        long value = longValue(CLAIM_LEASE_SECONDS, startup.getWebhookClaimLeaseSeconds());
        if (value < 15 || value > 3600) throw invalid(CLAIM_LEASE_SECONDS);
        return value;
    }

    public Duration reconcileDelay() {
        long value = longValue(RECONCILE_DELAY_MS, startup.getWebhookReconcileDelayMs());
        if (value < 250 || value > 3_600_000) throw invalid(RECONCILE_DELAY_MS);
        return Duration.ofMillis(value);
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }

    private int integerValue(String key, int fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.integerValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.integerValue(SET_KEY, key).orElse(fallback);
    }
    private long longValue(String key, long fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.longValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.longValue(SET_KEY, key).orElse(fallback);
    }
    private boolean runtimeRequired(String key) { return authority.isRuntimeAuthoritative(key); }
    private void requireSnapshot(String key) {
        if (!values.hasSnapshot(SET_KEY)) throw incomplete(key + ": authenticated local snapshot missing");
    }
    private static IllegalStateException invalid(String key) {
        return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED " + key);
    }
    private static IllegalStateException incomplete(String detail) {
        return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " detail=" + detail);
    }
}
