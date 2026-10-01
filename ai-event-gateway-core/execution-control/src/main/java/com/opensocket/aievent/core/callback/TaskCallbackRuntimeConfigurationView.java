package com.opensocket.aievent.core.callback;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/**
 * V41-C3R2A typed local-snapshot view for Task callback and dispatch-recovery policy.
 *
 * <p>During MIGRATION_READY a present authenticated local snapshot is preferred while the
 * startup binding remains a legal fallback. After a key reaches MIGRATED, the local snapshot
 * becomes mandatory and a missing value fails closed instead of silently reactivating YAML/ENV.</p>
 */
@Component
public class TaskCallbackRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.TASK_SYSTEM;

    public static final String IDEMPOTENCY_ENABLED = "task.callback.idempotency-enabled";
    public static final String REPLAY_PROTECTION_ENABLED = "task.callback.replay-protection-enabled";
    public static final String REJECT_REPLAY_MISMATCH = "task.callback.reject-callback-id-replay-mismatch";
    public static final String ALLOW_MISSING_DISPATCH_REQUEST_ID = "task.callback.allow-missing-dispatch-request-id";
    public static final String ENFORCE_STATE_TRANSITION = "task.callback.enforce-state-transition";
    public static final String REJECT_OLD_ATTEMPT_CALLBACKS = "task.callback.reject-old-attempt-callbacks";
    public static final String REQUIRE_ATTEMPT_NO = "task.callback.require-attempt-no";
    public static final String ENFORCE_GATEWAY_AGENT_IDENTITY = "task.callback.enforce-gateway-and-agent-identity";
    public static final String ALLOW_TERMINAL_CALLBACK_OVERRIDE = "task.callback.allow-terminal-callback-override";
    public static final String MAX_RECENT = "task.callback.max-recent";

    public static final String RECOVERY_TIMEOUT_ENABLED = "task.callback.recovery.timeout-enabled";
    public static final String RECOVERY_SCAN_INTERVAL_MS = "task.callback.recovery.scan-interval-ms";
    public static final String RECOVERY_DISPATCH_TIMEOUT = "task.callback.recovery.dispatch-timeout";
    public static final String RECOVERY_AUTO_FAIL_TIMED_OUT = "task.callback.recovery.auto-fail-timed-out";
    public static final String RECOVERY_RETRY_ENABLED = "task.callback.recovery.retry-enabled";
    public static final String RECOVERY_MAX_ATTEMPTS = "task.callback.recovery.max-attempts";
    public static final String RECOVERY_INITIAL_BACKOFF = "task.callback.recovery.initial-backoff";
    public static final String RECOVERY_JITTER_PERCENT = "task.callback.recovery.jitter-percent";
    public static final String RECOVERY_MAX_BACKOFF = "task.callback.recovery.max-backoff";
    public static final String RECOVERY_MAX_BATCH_SIZE = "task.callback.recovery.max-batch-size";

    public static final Set<String> ALL = Set.of(
            IDEMPOTENCY_ENABLED, REPLAY_PROTECTION_ENABLED, REJECT_REPLAY_MISMATCH,
            ALLOW_MISSING_DISPATCH_REQUEST_ID, ENFORCE_STATE_TRANSITION,
            REJECT_OLD_ATTEMPT_CALLBACKS, REQUIRE_ATTEMPT_NO, ENFORCE_GATEWAY_AGENT_IDENTITY,
            ALLOW_TERMINAL_CALLBACK_OVERRIDE, MAX_RECENT,
            RECOVERY_TIMEOUT_ENABLED, RECOVERY_SCAN_INTERVAL_MS, RECOVERY_DISPATCH_TIMEOUT,
            RECOVERY_AUTO_FAIL_TIMED_OUT, RECOVERY_RETRY_ENABLED, RECOVERY_MAX_ATTEMPTS,
            RECOVERY_INITIAL_BACKOFF, RECOVERY_JITTER_PERCENT, RECOVERY_MAX_BACKOFF,
            RECOVERY_MAX_BATCH_SIZE);

    private final TaskCallbackProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public TaskCallbackRuntimeConfigurationView(
            TaskCallbackProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }

    public boolean idempotencyEnabled() { return booleanValue(IDEMPOTENCY_ENABLED, startup.isIdempotencyEnabled()); }
    public boolean replayProtectionEnabled() { return booleanValue(REPLAY_PROTECTION_ENABLED, startup.isReplayProtectionEnabled()); }
    public boolean rejectCallbackIdReplayMismatch() { return booleanValue(REJECT_REPLAY_MISMATCH, startup.isRejectCallbackIdReplayMismatch()); }
    public boolean allowMissingDispatchRequestId() { return booleanValue(ALLOW_MISSING_DISPATCH_REQUEST_ID, startup.isAllowMissingDispatchRequestId()); }
    public boolean enforceStateTransition() { return booleanValue(ENFORCE_STATE_TRANSITION, startup.isEnforceStateTransition()); }
    public boolean rejectOldAttemptCallbacks() { return booleanValue(REJECT_OLD_ATTEMPT_CALLBACKS, startup.isRejectOldAttemptCallbacks()); }
    public boolean requireAttemptNo() { return booleanValue(REQUIRE_ATTEMPT_NO, startup.isRequireAttemptNo()); }
    public boolean enforceGatewayAndAgentIdentity() { return booleanValue(ENFORCE_GATEWAY_AGENT_IDENTITY, startup.isEnforceGatewayAndAgentIdentity()); }
    public boolean allowTerminalCallbackOverride() { return booleanValue(ALLOW_TERMINAL_CALLBACK_OVERRIDE, startup.isAllowTerminalCallbackOverride()); }

    public int maxRecent() {
        int value = integerValue(MAX_RECENT, startup.getMaxRecent());
        if (value < 1 || value > 5000) throw invalid(MAX_RECENT);
        return value;
    }

    public boolean recoveryTimeoutEnabled() { return booleanValue(RECOVERY_TIMEOUT_ENABLED, startup.getRecovery().isTimeoutEnabled()); }

    public Duration recoveryScanInterval() {
        long value = longValue(RECOVERY_SCAN_INTERVAL_MS, startup.getRecovery().getScanIntervalMs());
        if (value < 1000 || value > 3_600_000) throw invalid(RECOVERY_SCAN_INTERVAL_MS);
        return Duration.ofMillis(value);
    }

    public Duration recoveryDispatchTimeout() {
        return boundedDuration(RECOVERY_DISPATCH_TIMEOUT, startup.getRecovery().getDispatchTimeout(), Duration.ofSeconds(1), Duration.ofDays(7));
    }

    public boolean recoveryAutoFailTimedOut() { return booleanValue(RECOVERY_AUTO_FAIL_TIMED_OUT, startup.getRecovery().isAutoFailTimedOut()); }
    public boolean recoveryRetryEnabled() { return booleanValue(RECOVERY_RETRY_ENABLED, startup.getRecovery().isRetryEnabled()); }

    public int recoveryMaxAttempts() {
        int value = integerValue(RECOVERY_MAX_ATTEMPTS, startup.getRecovery().getMaxAttempts());
        if (value < 1 || value > 20) throw invalid(RECOVERY_MAX_ATTEMPTS);
        return value;
    }

    public Duration recoveryInitialBackoff() {
        return boundedDuration(RECOVERY_INITIAL_BACKOFF, startup.getRecovery().getInitialBackoff(), Duration.ofMillis(1), Duration.ofHours(1));
    }

    public int recoveryJitterPercent() {
        int value = integerValue(RECOVERY_JITTER_PERCENT, startup.getRecovery().getJitterPercent());
        if (value < 0 || value > 100) throw invalid(RECOVERY_JITTER_PERCENT);
        return value;
    }

    public Duration recoveryMaxBackoff() {
        Duration initial = recoveryInitialBackoff();
        Duration value = boundedDuration(RECOVERY_MAX_BACKOFF, startup.getRecovery().getMaxBackoff(), Duration.ofMillis(1), Duration.ofDays(1));
        if (value.compareTo(initial) < 0) throw invalid(RECOVERY_MAX_BACKOFF);
        return value;
    }

    public int recoveryMaxBatchSize() {
        int value = integerValue(RECOVERY_MAX_BATCH_SIZE, startup.getRecovery().getMaxBatchSize());
        if (value < 1 || value > 1000) throw invalid(RECOVERY_MAX_BATCH_SIZE);
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
