package com.opensocket.aievent.core.action.executor;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** Typed runtime view for Adapter Executor operational tuning. */
@Component
public class AdapterExecutorRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.ADAPTER_EXECUTION_SYSTEM;

    public static final String AUDIT_PAYLOAD_SNAPSHOT_ENABLED = "adapter-executor.audit.payload-snapshot-enabled";
    public static final String AUTO_EXECUTE_INTERVAL = "adapter-executor.auto-execute-interval";
    public static final String BATCH_SIZE = "adapter-executor.batch-size";
    public static final String EXECUTION_TIMEOUT = "adapter-executor.execution-timeout";
    public static final String INITIAL_BACKOFF = "adapter-executor.initial-backoff";
    public static final String MAX_ATTEMPTS = "adapter-executor.max-attempts";
    public static final String MAX_BACKOFF = "adapter-executor.max-backoff";
    public static final String MARK_UNAVAILABLE = "adapter-executor.mark-unavailable-when-no-executor";
    public static final String ISSUE_AUTO_EXECUTE = "adapter-executor.issue.auto-execute-pending";
    public static final String ISSUE_CONNECTOR_ENABLED = "adapter-executor.issue.connector-runtime-enabled";
    public static final String ISSUE_DEFAULT_VENDOR = "adapter-executor.issue.default-vendor";
    public static final String ISSUE_RECONCILE_ENABLED = "adapter-executor.issue.link-projection-reconciliation-enabled";
    public static final String ISSUE_RECONCILE_DELAY = "adapter-executor.issue.link-projection-reconciliation-delay";
    public static final String ISSUE_RECONCILE_BATCH = "adapter-executor.issue.link-projection-batch-size";
    public static final String ISSUE_RECONCILE_MAX_ATTEMPTS = "adapter-executor.issue.link-projection-max-attempts";
    public static final String ISSUE_RECONCILE_INITIAL_BACKOFF = "adapter-executor.issue.link-projection-initial-backoff";
    public static final String ISSUE_RECONCILE_MAX_BACKOFF = "adapter-executor.issue.link-projection-max-backoff";
    public static final String MCP_ENDPOINT_URL = "adapter-executor.mcp.endpoint-url";
    public static final String MCP_EXECUTOR_NAME = "adapter-executor.mcp.executor-name";
    public static final String MCP_HTTP_ENABLED = "adapter-executor.mcp.http-enabled";
    public static final String MCP_TIMEOUT = "adapter-executor.mcp.timeout";

    private static final Set<String> BASE_REQUIRED = Set.of(BATCH_SIZE, EXECUTION_TIMEOUT, INITIAL_BACKOFF, MAX_ATTEMPTS, MAX_BACKOFF);

    private final AdapterActionExecutionProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    @Autowired
    public AdapterExecutorRuntimeConfigurationView(AdapterActionExecutionProperties startup,
            RuntimeConfigurationSnapshotValues values, RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    AdapterExecutorRuntimeConfigurationView(AdapterActionExecutionProperties startup, RuntimeConfigurationSnapshotValues values) {
        this(startup, values, new RuntimeConfigurationAuthorityRegistry());
    }

    public boolean runtimeBacked() { return values != null && values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values == null ? null : values.revisionId(SET_KEY).orElse(null); }

    public int batchSize() {
        int v = integerValue(BATCH_SIZE, startup.getBatchSize());
        if (v < 1 || v > 1000) throw invalid(BATCH_SIZE);
        return v;
    }
    public Duration executionTimeout() { return boundedDuration(EXECUTION_TIMEOUT, durationValue(EXECUTION_TIMEOUT, startup.getExecutionTimeout()), Duration.ofMillis(1), Duration.ofHours(24)); }
    public Duration initialBackoff() { return boundedDuration(INITIAL_BACKOFF, durationValue(INITIAL_BACKOFF, startup.getInitialBackoff()), Duration.ofMillis(1), Duration.ofHours(24)); }
    public int maxAttempts() {
        int v = integerValue(MAX_ATTEMPTS, startup.getMaxAttempts());
        if (v < 1 || v > 1000) throw invalid(MAX_ATTEMPTS);
        return v;
    }
    public Duration maxBackoff() {
        Duration initial = initialBackoff();
        Duration d = boundedDuration(MAX_BACKOFF, durationValue(MAX_BACKOFF, startup.getMaxBackoff()), Duration.ofMillis(1), Duration.ofHours(24));
        if (d.compareTo(initial) < 0) throw invalid(MAX_BACKOFF);
        return d;
    }

    public boolean auditPayloadSnapshotEnabled() { return booleanValue(AUDIT_PAYLOAD_SNAPSHOT_ENABLED, startup.getAudit().isPayloadSnapshotEnabled()); }
    public Duration autoExecuteInterval() {
        Duration d = durationValue(AUTO_EXECUTE_INTERVAL, startup.getAutoExecuteInterval());
        if (d.compareTo(Duration.ofMillis(250)) < 0 || d.compareTo(Duration.ofHours(1)) > 0) throw invalid(AUTO_EXECUTE_INTERVAL);
        return d;
    }
    public boolean markUnavailableWhenNoExecutor() { return booleanValue(MARK_UNAVAILABLE, startup.isMarkUnavailableWhenNoExecutor()); }
    public boolean issueAutoExecutePending() { return booleanValue(ISSUE_AUTO_EXECUTE, startup.getIssue().isAutoExecutePending()); }
    public boolean issueConnectorRuntimeEnabled() { return booleanValue(ISSUE_CONNECTOR_ENABLED, startup.getIssue().isConnectorRuntimeEnabled()); }
    public String issueDefaultVendor() { return textValue(ISSUE_DEFAULT_VENDOR, startup.getIssue().getDefaultVendor()).trim(); }
    public boolean issueLinkProjectionReconciliationEnabled() { return booleanValue(ISSUE_RECONCILE_ENABLED, startup.getIssue().isLinkProjectionReconciliationEnabled()); }
    public Duration issueLinkProjectionReconciliationDelay() {
        Duration d = durationValue(ISSUE_RECONCILE_DELAY, startup.getIssue().getLinkProjectionReconciliationDelay());
        if (d.compareTo(Duration.ofMillis(250)) < 0 || d.compareTo(Duration.ofHours(1)) > 0) throw invalid(ISSUE_RECONCILE_DELAY);
        return d;
    }
    public int issueLinkProjectionBatchSize() {
        int v = integerValue(ISSUE_RECONCILE_BATCH, startup.getIssue().getLinkProjectionBatchSize());
        if (v < 1 || v > 1000) throw invalid(ISSUE_RECONCILE_BATCH);
        return v;
    }
    public int issueLinkProjectionMaxAttempts() {
        int v = integerValue(ISSUE_RECONCILE_MAX_ATTEMPTS, startup.getIssue().getLinkProjectionMaxAttempts());
        if (v < 1 || v > 1000) throw invalid(ISSUE_RECONCILE_MAX_ATTEMPTS);
        return v;
    }
    public Duration issueLinkProjectionInitialBackoff() { return positive(durationValue(ISSUE_RECONCILE_INITIAL_BACKOFF, startup.getIssue().getLinkProjectionInitialBackoff()), ISSUE_RECONCILE_INITIAL_BACKOFF); }
    public Duration issueLinkProjectionMaxBackoff() {
        Duration initial = issueLinkProjectionInitialBackoff();
        Duration d = positive(durationValue(ISSUE_RECONCILE_MAX_BACKOFF, startup.getIssue().getLinkProjectionMaxBackoff()), ISSUE_RECONCILE_MAX_BACKOFF);
        if (d.compareTo(initial) < 0) throw invalid(ISSUE_RECONCILE_MAX_BACKOFF);
        return d;
    }
    public boolean mcpHttpEnabled() { return booleanValue(MCP_HTTP_ENABLED, startup.getMcp().isHttpEnabled()); }
    public String mcpExecutorName() {
        String value = textValue(MCP_EXECUTOR_NAME, startup.getMcp().getExecutorName()).trim();
        if (value.isBlank()) throw invalid(MCP_EXECUTOR_NAME);
        return value;
    }
    public String mcpEndpointUrl() { return textValue(MCP_ENDPOINT_URL, startup.getMcp().getEndpointUrl()).trim(); }
    public Duration mcpTimeout() { return positive(durationValue(MCP_TIMEOUT, startup.getMcp().getTimeout()), MCP_TIMEOUT); }

    public boolean circuitBreakerRuntimeBacked() {
        return authority.areRuntimeAuthoritative(AdapterExecutorCircuitBreakerRuntimeKeys.ALL)
                && values != null && values.hasSnapshot(SET_KEY)
                && values.keys(SET_KEY).containsAll(AdapterExecutorCircuitBreakerRuntimeKeys.ALL);
    }
    public boolean circuitBreakerEnabled() {
        if (!circuitBreakerAuthorityActive()) return startup.getCircuitBreaker().isEnabled();
        ensureCircuitBreakerAuthority();
        return values.booleanValue(SET_KEY, AdapterExecutorCircuitBreakerRuntimeKeys.ENABLED)
                .orElseThrow(() -> incomplete(AdapterExecutorCircuitBreakerRuntimeKeys.ENABLED));
    }
    public int circuitBreakerFailureThreshold() {
        if (!circuitBreakerAuthorityActive()) return startup.getCircuitBreaker().getFailureThreshold();
        ensureCircuitBreakerAuthority();
        int v = values.integerValue(SET_KEY, AdapterExecutorCircuitBreakerRuntimeKeys.FAILURE_THRESHOLD)
                .orElseThrow(() -> incomplete(AdapterExecutorCircuitBreakerRuntimeKeys.FAILURE_THRESHOLD));
        if (v < 1 || v > 1000) throw invalid(AdapterExecutorCircuitBreakerRuntimeKeys.FAILURE_THRESHOLD);
        return v;
    }
    public Duration circuitBreakerOpenDuration() {
        if (!circuitBreakerAuthorityActive()) return startup.getCircuitBreaker().getOpenDuration();
        ensureCircuitBreakerAuthority();
        Duration d = values.durationValue(SET_KEY, AdapterExecutorCircuitBreakerRuntimeKeys.OPEN_DURATION)
                .orElseThrow(() -> incomplete(AdapterExecutorCircuitBreakerRuntimeKeys.OPEN_DURATION));
        return boundedDuration(AdapterExecutorCircuitBreakerRuntimeKeys.OPEN_DURATION, d, Duration.ofMillis(1), Duration.ofHours(24));
    }
    private boolean circuitBreakerAuthorityActive() { return authority.areRuntimeAuthoritative(AdapterExecutorCircuitBreakerRuntimeKeys.ALL); }
    private void ensureCircuitBreakerAuthority() {
        if (values == null || !values.hasSnapshot(SET_KEY)) throw incomplete("authenticated local snapshot is missing");
        values.requireKeys(SET_KEY, AdapterExecutorCircuitBreakerRuntimeKeys.ALL);
    }

    private boolean booleanValue(String key, boolean fallback) {
        if (runtimeRequired(key)) { requireKey(key); return values.booleanValue(SET_KEY, key).orElseThrow(() -> incomplete(key)); }
        return values == null ? fallback : values.booleanValue(SET_KEY, key).orElse(fallback);
    }
    private int integerValue(String key, int fallback) {
        if (runtimeRequired(key)) { requireKey(key); return values.integerValue(SET_KEY, key).orElseThrow(() -> incomplete(key)); }
        return values == null ? fallback : values.integerValue(SET_KEY, key).orElse(fallback);
    }
    private Duration durationValue(String key, Duration fallback) {
        if (runtimeRequired(key)) { requireKey(key); return values.durationValue(SET_KEY, key).orElseThrow(() -> incomplete(key)); }
        return values == null ? fallback : values.durationValue(SET_KEY, key).orElse(fallback);
    }
    private String textValue(String key, String fallback) {
        if (runtimeRequired(key)) { requireKey(key); return values.textValue(SET_KEY, key).orElseThrow(() -> incomplete(key)); }
        return values == null ? fallback : values.textValue(SET_KEY, key).orElse(fallback);
    }
    private boolean runtimeRequired(String key) { return authority != null && authority.isRuntimeAuthoritative(key); }
    private void requireKey(String key) {
        if (values == null || !values.hasSnapshot(SET_KEY)) throw incomplete(key + ": snapshot missing");
        if (!values.keys(SET_KEY).contains(key)) throw incomplete(key + ": key missing");
    }
    private void ensureBase() { if (runtimeBacked()) values.requireKeys(SET_KEY, BASE_REQUIRED); }
    private static Duration positive(Duration d, String key) { if (d == null || d.isZero() || d.isNegative()) throw invalid(key); return d; }
    private static Duration boundedDuration(String key, Duration value, Duration minimum, Duration maximum) { if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) throw invalid(key); return value; }
    private static IllegalStateException invalid(String key) { return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED " + key); }
    private static IllegalStateException incomplete(String detail) { return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " detail=" + detail); }
}
