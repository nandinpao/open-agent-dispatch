package com.opensocket.aievent.core.lifecycle;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/**
 * V41-C3B1 typed local-snapshot view for Task lifecycle operational configuration.
 *
 * <p>Before governance cutover, a present runtime snapshot wins while the startup binding remains
 * a legal fallback. After an individual key reaches MIGRATED, the authenticated local snapshot is
 * the only runtime authority and a missing value fails closed with CONFIGURATION_INCOMPLETE.</p>
 */
@Component
public class TaskLifecycleRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.TASK_SYSTEM;
    public static final String TIMEOUT_ENABLED = "core.lifecycle.task.timeout-enabled";
    public static final String AUTO_REASSIGN_ENABLED = "core.lifecycle.task.auto-reassign-enabled";
    public static final String SCAN_INTERVAL_MS = "core.lifecycle.task.scan-interval-ms";
    public static final String CREATED_TIMEOUT = "core.lifecycle.task.created-timeout";
    public static final String ASSIGNED_TIMEOUT = "core.lifecycle.task.assigned-timeout";
    public static final String DISPATCHED_TIMEOUT = "core.lifecycle.task.dispatched-timeout";
    public static final String RUNNING_TIMEOUT = "core.lifecycle.task.running-timeout";
    public static final String MAX_REASSIGNMENTS = "core.lifecycle.task.max-reassignments";
    public static final String MAX_BATCH_SIZE = "core.lifecycle.task.max-batch-size";

    public static final Set<String> ALL = Set.of(
            TIMEOUT_ENABLED, AUTO_REASSIGN_ENABLED, SCAN_INTERVAL_MS, CREATED_TIMEOUT,
            ASSIGNED_TIMEOUT, DISPATCHED_TIMEOUT, RUNNING_TIMEOUT, MAX_REASSIGNMENTS, MAX_BATCH_SIZE);

    private final LifecycleProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public TaskLifecycleRuntimeConfigurationView(
            LifecycleProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }

    public boolean timeoutEnabled() {
        return booleanValue(TIMEOUT_ENABLED, startup.getTask().isTimeoutEnabled());
    }

    public boolean autoReassignEnabled() {
        return booleanValue(AUTO_REASSIGN_ENABLED, startup.getTask().isAutoReassignEnabled());
    }

    public Duration scanInterval() {
        long value = longValue(SCAN_INTERVAL_MS, startup.getTask().getScanIntervalMs());
        if (value < 1000 || value > 3_600_000) throw invalid(SCAN_INTERVAL_MS);
        return Duration.ofMillis(value);
    }

    public Duration createdTimeout() {
        return boundedDuration(CREATED_TIMEOUT, startup.getTask().getCreatedTimeout(), Duration.ofSeconds(1), Duration.ofHours(24));
    }

    public Duration assignedTimeout() {
        return boundedDuration(ASSIGNED_TIMEOUT, startup.getTask().getAssignedTimeout(), Duration.ofSeconds(1), Duration.ofHours(24));
    }

    public Duration dispatchedTimeout() {
        return boundedDuration(DISPATCHED_TIMEOUT, startup.getTask().getDispatchedTimeout(), Duration.ofSeconds(1), Duration.ofHours(24));
    }

    public Duration runningTimeout() {
        return boundedDuration(RUNNING_TIMEOUT, startup.getTask().getRunningTimeout(), Duration.ofSeconds(1), Duration.ofDays(7));
    }

    public int maxReassignments() {
        int value = integerValue(MAX_REASSIGNMENTS, startup.getTask().getMaxReassignments());
        if (value < 0 || value > 20) throw invalid(MAX_REASSIGNMENTS);
        return value;
    }

    public int maxBatchSize() {
        int value = integerValue(MAX_BATCH_SIZE, startup.getTask().getMaxBatchSize());
        if (value < 1 || value > 1000) throw invalid(MAX_BATCH_SIZE);
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

    private Duration durationValue(String key, Duration fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.durationValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.durationValue(SET_KEY, key).orElse(fallback);
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
