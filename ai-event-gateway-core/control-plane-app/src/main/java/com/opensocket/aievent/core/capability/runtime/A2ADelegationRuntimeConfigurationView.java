package com.opensocket.aievent.core.capability.runtime;

import java.time.Duration;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/**
 * V41-C3R2G typed runtime view for active A2A / delegation operational controls.
 * Node identity, worker identity, public callback URL and legacy reconcilers intentionally stay
 * outside this runtime set.
 */
@Component
public final class A2ADelegationRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.A2A_DELEGATION_SYSTEM;

    public static final String PUSH_MAX_BODY_BYTES = "a2a.push.ingress.max-body-bytes";
    public static final String RECONCILIATION_PIPELINE_MS = "a2a.reconciliation.pipeline-ms";
    public static final String ASYNC_ENABLED = "opendispatch.a2a-async.enabled";
    public static final String ASYNC_BATCH_SIZE = "opendispatch.a2a-async.batch-size";
    public static final String ASYNC_POLL_MS = "opendispatch.a2a-async.poll-ms";
    public static final String PUSH_HANDOFF_BATCH_SIZE = "opendispatch.a2a-async.push-handoff-batch-size";
    public static final String PUSH_HANDOFF_MS = "opendispatch.a2a-async.push-handoff-ms";
    public static final String READ_ENABLED = "opendispatch.a2a-read.enabled";
    public static final String READ_BATCH_SIZE = "opendispatch.a2a-read.batch-size";
    public static final String READ_POLL_MS = "opendispatch.a2a-read.poll-ms";
    public static final String RESULT_RETRY_ENABLED = "opendispatch.capability-result-retry.enabled";
    public static final String RESULT_RETRY_BATCH_SIZE = "opendispatch.capability-result-retry.batch-size";
    public static final String RESULT_RETRY_POLL_MS = "opendispatch.capability-result-retry.poll-ms";
    public static final String MCP_READ_ENABLED = "opendispatch.mcp-read.enabled";
    public static final String MCP_READ_BATCH_SIZE = "opendispatch.mcp-read.batch-size";
    public static final String MCP_READ_POLL_MS = "opendispatch.mcp-read.poll-ms";
    public static final String PLAN_RUNTIME_BATCH_SIZE = "opendispatch.plan-runtime.batch-size";
    public static final String PLAN_RUNTIME_POLL_MS = "opendispatch.plan-runtime.poll-ms";
    public static final String AUTHORITY_REVOCATION_SWEEP_MS = "opendispatch.a2a-authority-revocation-sweep-ms";

    public static final Set<String> ALL = Set.of(
            PUSH_MAX_BODY_BYTES, RECONCILIATION_PIPELINE_MS,
            ASYNC_ENABLED, ASYNC_BATCH_SIZE, ASYNC_POLL_MS, PUSH_HANDOFF_BATCH_SIZE, PUSH_HANDOFF_MS,
            READ_ENABLED, READ_BATCH_SIZE, READ_POLL_MS,
            RESULT_RETRY_ENABLED, RESULT_RETRY_BATCH_SIZE, RESULT_RETRY_POLL_MS,
            MCP_READ_ENABLED, MCP_READ_BATCH_SIZE, MCP_READ_POLL_MS,
            PLAN_RUNTIME_BATCH_SIZE, PLAN_RUNTIME_POLL_MS, AUTHORITY_REVOCATION_SWEEP_MS);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final Environment startup;

    public A2ADelegationRuntimeConfigurationView(
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority,
            Environment startup) {
        this.values = values;
        this.authority = authority;
        this.startup = startup;
    }

    public int pushMaxBodyBytes() { return integer(PUSH_MAX_BODY_BYTES, 1_048_576, 1_024, 104_857_600); }
    public Duration reconciliationPipelineDelay() { return millis(RECONCILIATION_PIPELINE_MS, 30_000L, 250L, 3_600_000L); }
    public boolean asyncEnabled() { return bool(ASYNC_ENABLED, true); }
    public int asyncBatchSize() { return integer(ASYNC_BATCH_SIZE, 20, 1, 50); }
    public Duration asyncPollDelay() { return millis(ASYNC_POLL_MS, 3_000L, 250L, 3_600_000L); }
    public int pushHandoffBatchSize() { return integer(PUSH_HANDOFF_BATCH_SIZE, 20, 1, 50); }
    public Duration pushHandoffDelay() { return millis(PUSH_HANDOFF_MS, 1_000L, 100L, 3_600_000L); }
    public boolean readEnabled() { return bool(READ_ENABLED, true); }
    public int readBatchSize() { return integer(READ_BATCH_SIZE, 20, 1, 50); }
    public Duration readPollDelay() { return millis(READ_POLL_MS, 2_000L, 100L, 3_600_000L); }
    public boolean resultRetryEnabled() { return bool(RESULT_RETRY_ENABLED, true); }
    public int resultRetryBatchSize() { return integer(RESULT_RETRY_BATCH_SIZE, 50, 1, 100); }
    public Duration resultRetryPollDelay() { return millis(RESULT_RETRY_POLL_MS, 5_000L, 250L, 3_600_000L); }
    public boolean mcpReadEnabled() { return bool(MCP_READ_ENABLED, true); }
    public int mcpReadBatchSize() { return integer(MCP_READ_BATCH_SIZE, 20, 1, 50); }
    public Duration mcpReadPollDelay() { return millis(MCP_READ_POLL_MS, 2_000L, 100L, 3_600_000L); }
    public int planRuntimeBatchSize() { return integer(PLAN_RUNTIME_BATCH_SIZE, 20, 1, 100); }
    public Duration planRuntimePollDelay() { return millis(PLAN_RUNTIME_POLL_MS, 1_500L, 100L, 3_600_000L); }
    public Duration authorityRevocationSweepDelay() { return millis(AUTHORITY_REVOCATION_SWEEP_MS, 30_000L, 250L, 3_600_000L); }

    private boolean bool(String key, boolean fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot();
            return values.booleanValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.booleanValue(SET_KEY, key).orElse(startup.getProperty(key, Boolean.class, fallback));
    }

    private int integer(String key, int fallback, int min, int max) {
        long value;
        if (runtimeRequired(key)) {
            requireSnapshot();
            value = values.longValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        } else {
            value = values.longValue(SET_KEY, key).orElse(startup.getProperty(key, Long.class, (long) fallback));
        }
        if (value < min || value > max) throw invalid(key, value);
        return Math.toIntExact(value);
    }

    private Duration millis(String key, long fallback, long min, long max) {
        long value;
        if (runtimeRequired(key)) {
            requireSnapshot();
            value = values.longValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        } else {
            value = values.longValue(SET_KEY, key).orElse(startup.getProperty(key, Long.class, fallback));
        }
        if (value < min || value > max) throw invalid(key, value);
        return Duration.ofMillis(value);
    }

    private boolean runtimeRequired(String key) {
        return authority != null && authority.isRuntimeAuthoritative(key);
    }

    private void requireSnapshot() {
        if (!values.hasSnapshot(SET_KEY)) throw incomplete("*");
        values.requireKeys(SET_KEY, ALL);
    }

    private static IllegalStateException invalid(String key, Object value) {
        return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key + " value=" + value);
    }

    private static IllegalStateException incomplete(String key) {
        return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " key=" + key);
    }
}
