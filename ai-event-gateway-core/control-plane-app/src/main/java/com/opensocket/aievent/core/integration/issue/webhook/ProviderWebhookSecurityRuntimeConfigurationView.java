package com.opensocket.aievent.core.integration.issue.webhook;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/**
 * Typed local-snapshot view for Provider Webhook ingress limits and maintenance cadence.
 *
 * <p>"enabled" is an ingress kill switch: false rejects provider Webhooks with 503; it never
 * bypasses signature verification. Before C3R3, YAML/ENV remains a migration fallback. After
 * cutover, missing authoritative values fail closed.</p>
 */
@Component
public final class ProviderWebhookSecurityRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.INTEGRATION_SYNC_SYSTEM;
    public static final String ENABLED = "integration-sync.webhook-security.enabled";
    public static final String DEFAULT_MAX_BODY_BYTES = "integration-sync.webhook-security.default-max-body-bytes";
    public static final String DEFAULT_RATE_LIMIT_PER_MINUTE = "integration-sync.webhook-security.default-rate-limit-per-minute";
    public static final String CLOCK_SKEW_SECONDS = "integration-sync.webhook-security.clock-skew-seconds";
    public static final String NONCE_CLEANUP_INTERVAL_MS = "integration-sync.webhook-security.nonce-cleanup-interval-ms";
    public static final String NONCE_CLEANUP_BATCH_SIZE = "integration-sync.webhook-security.nonce-cleanup-batch-size";

    private final ProviderWebhookSecurityProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public ProviderWebhookSecurityRuntimeConfigurationView(
            ProviderWebhookSecurityProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }
    public boolean enabled() { return booleanValue(ENABLED, startup.isEnabled()); }

    public int defaultMaxBodyBytes() {
        int value = integerValue(DEFAULT_MAX_BODY_BYTES, startup.getDefaultMaxBodyBytes());
        if (value < 1024 || value > 104_857_600) throw invalid(DEFAULT_MAX_BODY_BYTES);
        return value;
    }

    public int defaultRateLimitPerMinute() {
        int value = integerValue(DEFAULT_RATE_LIMIT_PER_MINUTE, startup.getDefaultRateLimitPerMinute());
        if (value < 1 || value > 100_000) throw invalid(DEFAULT_RATE_LIMIT_PER_MINUTE);
        return value;
    }

    public long clockSkewSeconds() {
        long value = longValue(CLOCK_SKEW_SECONDS, startup.getClockSkewSeconds());
        if (value < 0 || value > 3600) throw invalid(CLOCK_SKEW_SECONDS);
        return value;
    }

    public Duration nonceCleanupInterval() {
        long value = longValue(NONCE_CLEANUP_INTERVAL_MS, startup.getNonceCleanupIntervalMs());
        if (value < 1000 || value > 3_600_000) throw invalid(NONCE_CLEANUP_INTERVAL_MS);
        return Duration.ofMillis(value);
    }

    public int nonceCleanupBatchSize() {
        int value = integerValue(NONCE_CLEANUP_BATCH_SIZE, startup.getNonceCleanupBatchSize());
        if (value < 1 || value > 100_000) throw invalid(NONCE_CLEANUP_BATCH_SIZE);
        return value;
    }

    private boolean booleanValue(String key, boolean fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.booleanValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.booleanValue(SET_KEY, key).orElse(fallback);
    }

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
