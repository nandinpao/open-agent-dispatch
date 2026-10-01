package com.opensocket.aievent.core.task;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** Typed V41-C3R2A local-snapshot view for task-level delayed dispatch recovery. */
@Component
public class TaskDispatchRecoveryRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.TASK_SYSTEM;
    public static final String ENABLED = "task.dispatch-recovery.enabled";
    public static final String SCANNER_ENABLED = "task.dispatch-recovery.scanner-enabled";
    public static final String INTERVAL_MS = "task.dispatch-recovery.interval-ms";
    public static final String MAX_BATCH_SIZE = "task.dispatch-recovery.max-batch-size";
    public static final String MAX_ATTEMPTS = "task.dispatch-recovery.max-attempts";
    public static final String INITIAL_DELAY = "task.dispatch-recovery.initial-delay";
    public static final String MAX_DELAY = "task.dispatch-recovery.max-delay";
    public static final String CLAIM_LEASE = "task.dispatch-recovery.claim-lease";
    public static final String WORKER_ID = "task.dispatch-recovery.worker-id";

    public static final Set<String> ALL = Set.of(
            ENABLED, SCANNER_ENABLED, INTERVAL_MS, MAX_BATCH_SIZE, MAX_ATTEMPTS,
            INITIAL_DELAY, MAX_DELAY, CLAIM_LEASE, WORKER_ID);

    private final TaskDispatchRecoveryProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    @Autowired
    public TaskDispatchRecoveryRuntimeConfigurationView(
            TaskDispatchRecoveryProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    /** Compatibility constructor for focused tests created before the authority registry existed. */
    public TaskDispatchRecoveryRuntimeConfigurationView(
            TaskDispatchRecoveryProperties startup,
            RuntimeConfigurationSnapshotValues values) {
        this(startup, values, new RuntimeConfigurationAuthorityRegistry());
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }

    public boolean enabled() { return booleanValue(ENABLED, startup.isEnabled()); }
    public boolean scannerEnabled() { return booleanValue(SCANNER_ENABLED, startup.isScannerEnabled()); }

    public Duration scanInterval() {
        long value = longValue(INTERVAL_MS, startup.getIntervalMs());
        if (value < 250 || value > 3_600_000) throw invalid(INTERVAL_MS);
        return Duration.ofMillis(value);
    }

    public int maxBatchSize() {
        int value = integerValue(MAX_BATCH_SIZE, startup.getMaxBatchSize());
        if (value < 1 || value > 1000) throw invalid(MAX_BATCH_SIZE);
        return value;
    }

    public int maxAttempts() {
        int value = integerValue(MAX_ATTEMPTS, startup.getMaxAttempts());
        if (value < 0 || value > 1000) throw invalid(MAX_ATTEMPTS);
        return value;
    }

    public Duration initialDelay() {
        return boundedDuration(INITIAL_DELAY, startup.getInitialDelay(), Duration.ofMillis(1), Duration.ofDays(1));
    }

    public Duration maxDelay() {
        Duration initial = initialDelay();
        Duration value = boundedDuration(MAX_DELAY, startup.getMaxDelay(), Duration.ofMillis(1), Duration.ofDays(7));
        if (value.compareTo(initial) < 0) throw invalid(MAX_DELAY);
        return value;
    }

    public Duration claimLease() {
        return boundedDuration(CLAIM_LEASE, startup.getClaimLease(), Duration.ofMillis(250), Duration.ofHours(1));
    }

    public String workerId() {
        String value = textValue(WORKER_ID, startup.getWorkerId());
        if (value == null || value.isBlank() || value.length() > 128) throw invalid(WORKER_ID);
        return value.trim();
    }

    public Duration delayForAttempt(int attemptNo) {
        int attempt = Math.max(1, attemptNo);
        long multiplier = 1L << Math.max(0, Math.min(attempt - 1, 10));
        Duration candidate = initialDelay().multipliedBy(multiplier);
        Duration max = maxDelay();
        return candidate.compareTo(max) > 0 ? max : candidate;
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

    private Duration durationValue(String key, Duration fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.durationValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.durationValue(SET_KEY, key).orElse(fallback);
    }

    private String textValue(String key, String fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.textValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.textValue(SET_KEY, key).orElse(fallback);
    }

    private Duration boundedDuration(String key, Duration fallback, Duration minimum, Duration maximum) {
        Duration value = durationValue(key, fallback);
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) throw invalid(key);
        return value;
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
