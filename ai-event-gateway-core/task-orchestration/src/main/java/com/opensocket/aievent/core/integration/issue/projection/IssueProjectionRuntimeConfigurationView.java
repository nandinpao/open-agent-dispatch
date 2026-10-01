package com.opensocket.aievent.core.integration.issue.projection;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/**
 * Typed local-snapshot view for canonical Issue Projection runtime policy.
 *
 * <p>Before C3R3 cutover, an authenticated runtime value wins while the startup binding remains
 * a legal migration fallback. After a key becomes runtime-authoritative, a missing snapshot/value
 * fails closed with CONFIGURATION_INCOMPLETE and YAML/ENV cannot silently regain authority.</p>
 */
@Component
public class IssueProjectionRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.ISSUE_SYSTEM;
    public static final String ENABLED = "issue-projection.enabled";
    public static final String MAX_ATTEMPTS = "issue-projection.max-attempts";
    public static final String RECONCILE_BATCH_SIZE = "issue-projection.reconcile-batch-size";
    public static final String RETRY_DELAY_SECONDS = "issue-projection.retry-delay-seconds";
    public static final String RECONCILE_DELAY_MS = "issue-projection.reconcile-delay-ms";

    private final IssueProjectionProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public IssueProjectionRuntimeConfigurationView(
            IssueProjectionProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }

    public boolean enabled() { return booleanValue(ENABLED, startup.isEnabled()); }

    public int maxAttempts() {
        int value = integerValue(MAX_ATTEMPTS, startup.getMaxAttempts());
        if (value < 1 || value > 100) throw invalid(MAX_ATTEMPTS);
        return value;
    }

    public int reconcileBatchSize() {
        int value = integerValue(RECONCILE_BATCH_SIZE, startup.getReconcileBatchSize());
        if (value < 1 || value > 1000) throw invalid(RECONCILE_BATCH_SIZE);
        return value;
    }

    public long retryDelaySeconds() {
        long value = longValue(RETRY_DELAY_SECONDS, startup.getRetryDelaySeconds());
        if (value < 1 || value > 86400) throw invalid(RETRY_DELAY_SECONDS);
        return value;
    }

    public Duration reconcileDelay() {
        long value = longValue(RECONCILE_DELAY_MS, startup.getReconcileDelayMs());
        if (value < 250 || value > 3_600_000) throw invalid(RECONCILE_DELAY_MS);
        return Duration.ofMillis(value);
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
