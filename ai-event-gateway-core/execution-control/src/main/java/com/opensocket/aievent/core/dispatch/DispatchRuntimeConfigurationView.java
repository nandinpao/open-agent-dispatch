package com.opensocket.aievent.core.dispatch;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/**
 * Typed local-snapshot view for the complete Dispatch runtime policy surface.
 *
 * <p>V41-C3R2B closes all 26 Dispatch runtime targets. Before Single Authority cutover, an
 * authenticated runtime snapshot wins and startup YAML/ENV remains a temporary fallback. Once a
 * key is MIGRATED in {@link RuntimeConfigurationAuthorityRegistry}, the authenticated local
 * snapshot is the only legal authority and a missing snapshot/key fails closed.</p>
 */
@Component
public class DispatchRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.DISPATCH_SYSTEM;

    public static final String CLAIM_LEASE = "dispatch.claim-lease";
    public static final String CLIENT_AUTO_EXECUTE_INTERVAL_MS = "dispatch.client.auto-execute-interval-ms";
    public static final String CLIENT_CONNECT_TIMEOUT = "dispatch.client.connect-timeout";
    public static final String CLIENT_DEFAULT_GATEWAY_BASE_URL = "dispatch.client.default-gateway-base-url";
    public static final String CLIENT_GATEWAY_TNN_001 = "dispatch.client.gateway-base-urls.gateway-tnn-001";
    public static final String CLIENT_GATEWAY_TPE_001 = "dispatch.client.gateway-base-urls.gateway-tpe-001";
    public static final String CLIENT_GATEWAY_TYN_001 = "dispatch.client.gateway-base-urls.gateway-tyn-001";
    public static final String CLIENT_MAX_BATCH_SIZE = "dispatch.client.max-batch-size";
    public static final String CLIENT_REQUEST_TIMEOUT = "dispatch.client.request-timeout";
    public static final String EXECUTION_POLICY = "dispatch.execution-policy";
    public static final String GATEWAY_DISPATCH_PATH = "dispatch.gateway-dispatch-path";
    public static final String REQUIRE_ASSIGNABLE_AGENT = "dispatch.require-assignable-agent";
    public static final String REVIEW_MODE = "dispatch.review-mode";
    public static final String SOURCE_NODE_ID = "dispatch.source-node-id";
    public static final String WORKER_ID = "dispatch.worker-id";

    public static final String RETRY_ENABLED = "dispatch.retry.enabled";
    public static final String RETRY_MAX_ATTEMPTS = "dispatch.retry.max-attempts";
    public static final String RETRY_INITIAL_BACKOFF = "dispatch.retry.initial-backoff";
    public static final String RETRY_MAX_BACKOFF = "dispatch.retry.max-backoff";
    public static final String RETRY_JITTER_PERCENT = "dispatch.retry.jitter-percent";
    public static final String FAILURE_REQUEUE_ENABLED = "dispatch.failure-requeue.enabled";
    public static final String FAILURE_REQUEUE_MAX_REASSIGNMENTS = "dispatch.failure-requeue.max-reassignments";
    public static final String RUNTIME_INITIAL_BACKOFF = "dispatch.failure-requeue.runtime-initial-backoff";
    public static final String RUNTIME_MAX_BACKOFF = "dispatch.failure-requeue.runtime-max-backoff";
    public static final String RUNTIME_JITTER_PERCENT = "dispatch.failure-requeue.runtime-jitter-percent";
    public static final String POISON_AGENT_FAILURE_THRESHOLD = "dispatch.failure-requeue.poison-agent-failure-threshold";

    public static final Set<String> ALL = Set.of(
            CLAIM_LEASE,
            CLIENT_AUTO_EXECUTE_INTERVAL_MS,
            CLIENT_CONNECT_TIMEOUT,
            CLIENT_DEFAULT_GATEWAY_BASE_URL,
            CLIENT_GATEWAY_TNN_001,
            CLIENT_GATEWAY_TPE_001,
            CLIENT_GATEWAY_TYN_001,
            CLIENT_MAX_BATCH_SIZE,
            CLIENT_REQUEST_TIMEOUT,
            EXECUTION_POLICY,
            GATEWAY_DISPATCH_PATH,
            REQUIRE_ASSIGNABLE_AGENT,
            REVIEW_MODE,
            SOURCE_NODE_ID,
            WORKER_ID,
            RETRY_ENABLED,
            RETRY_MAX_ATTEMPTS,
            RETRY_INITIAL_BACKOFF,
            RETRY_MAX_BACKOFF,
            RETRY_JITTER_PERCENT,
            FAILURE_REQUEUE_ENABLED,
            FAILURE_REQUEUE_MAX_REASSIGNMENTS,
            RUNTIME_INITIAL_BACKOFF,
            RUNTIME_MAX_BACKOFF,
            RUNTIME_JITTER_PERCENT,
            POISON_AGENT_FAILURE_THRESHOLD);

    private static final Set<String> LEGACY_PILOT_KEYS = Set.of(
            RETRY_MAX_ATTEMPTS, RETRY_INITIAL_BACKOFF, RETRY_MAX_BACKOFF, RETRY_JITTER_PERCENT);

    private final DispatchProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    @Autowired
    public DispatchRuntimeConfigurationView(
            DispatchProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    /** Compatibility constructor for focused tests; no migrated keys are activated. */
    public DispatchRuntimeConfigurationView(DispatchProperties startup, RuntimeConfigurationSnapshotValues values) {
        this(startup, values, new RuntimeConfigurationAuthorityRegistry());
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }

    public Duration claimLease() {
        Duration value = durationValue(CLAIM_LEASE, startup.getClaimLease());
        if (value.compareTo(Duration.ofMillis(250)) < 0 || value.compareTo(Duration.ofHours(1)) > 0) {
            throw invalid(CLAIM_LEASE);
        }
        return value;
    }

    public Duration autoExecuteInterval() {
        long millis = longValue(CLIENT_AUTO_EXECUTE_INTERVAL_MS, startup.getClient().getAutoExecuteIntervalMs());
        if (millis < 250L || millis > 3_600_000L) throw invalid(CLIENT_AUTO_EXECUTE_INTERVAL_MS);
        return Duration.ofMillis(millis);
    }

    public Duration connectTimeout() {
        Duration value = durationValue(CLIENT_CONNECT_TIMEOUT, startup.getClient().getConnectTimeout());
        if (value.compareTo(Duration.ofMillis(100)) < 0 || value.compareTo(Duration.ofMinutes(5)) > 0) {
            throw invalid(CLIENT_CONNECT_TIMEOUT);
        }
        return value;
    }

    public Duration requestTimeout() {
        Duration value = durationValue(CLIENT_REQUEST_TIMEOUT, startup.getClient().getRequestTimeout());
        if (value.compareTo(Duration.ofMillis(100)) < 0 || value.compareTo(Duration.ofMinutes(30)) > 0) {
            throw invalid(CLIENT_REQUEST_TIMEOUT);
        }
        return value;
    }

    public int maxBatchSize() {
        int value = integerValue(CLIENT_MAX_BATCH_SIZE, startup.getClient().getMaxBatchSize());
        if (value < 1 || value > 1000) throw invalid(CLIENT_MAX_BATCH_SIZE);
        return value;
    }

    public String defaultGatewayBaseUrl() {
        return validateBaseUrl(textValue(CLIENT_DEFAULT_GATEWAY_BASE_URL, startup.getClient().getDefaultGatewayBaseUrl()), CLIENT_DEFAULT_GATEWAY_BASE_URL);
    }

    public Map<String, String> gatewayBaseUrls() {
        LinkedHashMap<String, String> resolved = new LinkedHashMap<>(startup.getClient().getGatewayBaseUrls());
        resolveGatewayUrl(resolved, "gateway-tpe-001", CLIENT_GATEWAY_TPE_001);
        resolveGatewayUrl(resolved, "gateway-tyn-001", CLIENT_GATEWAY_TYN_001);
        resolveGatewayUrl(resolved, "gateway-tnn-001", CLIENT_GATEWAY_TNN_001);
        return Map.copyOf(resolved);
    }

    public String gatewayBaseUrl(String gatewayNodeId) {
        String configured = gatewayNodeId == null ? null : gatewayBaseUrls().get(gatewayNodeId);
        return configured == null || configured.isBlank() ? defaultGatewayBaseUrl() : configured;
    }

    public DispatchExecutionPolicy executionPolicy() {
        String value = nonBlank(textValue(EXECUTION_POLICY, startup.getExecutionPolicy().name()), EXECUTION_POLICY);
        try { return DispatchExecutionPolicy.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException ex) { throw invalid(EXECUTION_POLICY); }
    }

    public DispatchReviewMode reviewMode() {
        String value = nonBlank(textValue(REVIEW_MODE, startup.getReviewMode().name()), REVIEW_MODE);
        try { return DispatchReviewMode.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException ex) { throw invalid(REVIEW_MODE); }
    }

    public String sourceNodeId() { return bounded(textValue(SOURCE_NODE_ID, startup.getSourceNodeId()), SOURCE_NODE_ID, 1, 128); }
    public String workerId() { return bounded(textValue(WORKER_ID, startup.getWorkerId()), WORKER_ID, 1, 128); }

    public String gatewayDispatchPath() {
        String path = bounded(textValue(GATEWAY_DISPATCH_PATH, startup.getGatewayDispatchPath()), GATEWAY_DISPATCH_PATH, 1, 512);
        if (!path.startsWith("/")) throw invalid(GATEWAY_DISPATCH_PATH);
        return path;
    }

    public boolean requireAssignableAgent() {
        return booleanValue(REQUIRE_ASSIGNABLE_AGENT, startup.isRequireAssignableAgent());
    }

    public boolean retryEnabled() { return booleanValue(RETRY_ENABLED, startup.getRetry().isEnabled()); }

    public int maxAttempts() {
        requireLegacyPilotSnapshotCompleteness();
        int value = integerValue(RETRY_MAX_ATTEMPTS, startup.getRetry().getMaxAttempts());
        if (value < 1 || value > 20) throw invalid(RETRY_MAX_ATTEMPTS);
        return value;
    }

    public Duration initialBackoff() {
        requireLegacyPilotSnapshotCompleteness();
        return positive(durationValue(RETRY_INITIAL_BACKOFF, startup.getRetry().getInitialBackoff()), RETRY_INITIAL_BACKOFF);
    }

    public Duration maxBackoff() {
        Duration initial = initialBackoff();
        Duration value = positive(durationValue(RETRY_MAX_BACKOFF, startup.getRetry().getMaxBackoff()), RETRY_MAX_BACKOFF);
        if (value.compareTo(initial) < 0) throw invalid(RETRY_MAX_BACKOFF + " must be >= initial-backoff");
        return value;
    }

    public int jitterPercent() {
        requireLegacyPilotSnapshotCompleteness();
        int value = integerValue(RETRY_JITTER_PERCENT, startup.getRetry().getJitterPercent());
        if (value < 0 || value > 100) throw invalid(RETRY_JITTER_PERCENT);
        return value;
    }

    public boolean failureRequeueEnabled() {
        return booleanValue(FAILURE_REQUEUE_ENABLED, startup.getFailureRequeue().isEnabled());
    }

    public int failureRequeueMaxReassignments() {
        int value = integerValue(FAILURE_REQUEUE_MAX_REASSIGNMENTS, startup.getFailureRequeue().getMaxReassignments());
        if (value < 0 || value > 20) throw invalid(FAILURE_REQUEUE_MAX_REASSIGNMENTS);
        return value;
    }

    public Duration runtimeInitialBackoff() {
        return positive(durationValue(RUNTIME_INITIAL_BACKOFF, startup.getFailureRequeue().getRuntimeInitialBackoff()), RUNTIME_INITIAL_BACKOFF);
    }

    public Duration runtimeMaxBackoff() {
        Duration initial = runtimeInitialBackoff();
        Duration value = positive(durationValue(RUNTIME_MAX_BACKOFF, startup.getFailureRequeue().getRuntimeMaxBackoff()), RUNTIME_MAX_BACKOFF);
        if (value.compareTo(initial) < 0) throw invalid(RUNTIME_MAX_BACKOFF + " must be >= runtime-initial-backoff");
        return value;
    }

    public int runtimeJitterPercent() {
        int value = integerValue(RUNTIME_JITTER_PERCENT, startup.getFailureRequeue().getRuntimeJitterPercent());
        if (value < 0 || value > 100) throw invalid(RUNTIME_JITTER_PERCENT);
        return value;
    }

    public int poisonAgentFailureThreshold() {
        int value = integerValue(POISON_AGENT_FAILURE_THRESHOLD, startup.getFailureRequeue().getPoisonAgentFailureThreshold());
        if (value < 1 || value > 100) throw invalid(POISON_AGENT_FAILURE_THRESHOLD);
        return value;
    }

    private void resolveGatewayUrl(Map<String, String> resolved, String gatewayNodeId, String key) {
        String fallback = resolved.get(gatewayNodeId);
        String value = textValue(key, fallback);
        if (value != null && !value.isBlank()) resolved.put(gatewayNodeId, validateBaseUrl(value, key));
        else resolved.remove(gatewayNodeId);
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

    private String textValue(String key, String fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.textValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.textValue(SET_KEY, key).orElse(fallback);
    }

    private Duration durationValue(String key, Duration fallback) {
        if (runtimeRequired(key)) {
            requireSnapshot(key);
            return values.durationValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.durationValue(SET_KEY, key).orElse(fallback);
    }

    private void requireLegacyPilotSnapshotCompleteness() {
        values.requireKeys(SET_KEY, LEGACY_PILOT_KEYS);
    }

    private boolean runtimeRequired(String key) { return authority.isRuntimeAuthoritative(key); }

    private void requireSnapshot(String key) {
        if (!values.hasSnapshot(SET_KEY)) throw incomplete(key + ": authenticated local snapshot missing");
    }

    private static Duration positive(Duration value, String key) {
        if (value == null || value.isZero() || value.isNegative()) throw invalid(key);
        return value;
    }

    private static String nonBlank(String value, String key) {
        if (value == null || value.isBlank()) throw invalid(key);
        return value.trim();
    }

    private static String bounded(String value, String key, int min, int max) {
        String normalized = nonBlank(value, key);
        if (normalized.length() < min || normalized.length() > max) throw invalid(key);
        return normalized;
    }

    private static String validateBaseUrl(String value, String key) {
        String normalized = bounded(value, key, 1, 2048);
        try {
            URI uri = URI.create(normalized);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()) throw invalid(key);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) throw invalid(key);
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalid(key);
        }
    }

    private static IllegalStateException invalid(String key) {
        return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED " + key);
    }

    private static IllegalStateException incomplete(String detail) {
        return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " detail=" + detail);
    }
}
