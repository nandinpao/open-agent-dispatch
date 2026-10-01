package com.opensocket.aievent.gateway.netty.runtime;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.authorization.CoreAgentAuthorizationProperties;
import com.opensocket.aievent.gateway.netty.config.CoreDirectorySyncProperties;
import com.opensocket.aievent.gateway.netty.config.CoreForwardProperties;
import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import com.opensocket.aievent.gateway.netty.config.CoreTaskCallbackRelayProperties;
import com.opensocket.aievent.gateway.netty.config.InboundEventCategory;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

/** C3R2I typed runtime view for active Gateway/Core operational behavior. */
@Component
public final class GatewayOperationalRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/GATEWAY/SYSTEM";
    public static final String AGENT_AUTH_TIMEOUT = "gateway.agent-authorization.timeout-ms";
    public static final String AGENT_AUTH_REJECTED_HISTORY = "gateway.agent-authorization.rejected-history-limit";
    public static final String DIRECTORY_HEARTBEAT_INTERVAL = "gateway.core-directory-sync.gateway-heartbeat-interval-ms";
    public static final String DIRECTORY_SNAPSHOT_INTERVAL = "gateway.core-directory-sync.snapshot-interval-ms";
    public static final String DIRECTORY_GATEWAY_LEASE_TTL = "gateway.core-directory-sync.gateway-lease-ttl-seconds";
    public static final String DIRECTORY_AGENT_LEASE_TTL = "gateway.core-directory-sync.agent-lease-ttl-seconds";
    public static final String DIRECTORY_DEFAULT_AGENT_MAX_TASKS = "gateway.core-directory-sync.default-agent-max-concurrent-tasks";
    public static final String DIRECTORY_DEFAULT_AGENT_HEALTH = "gateway.core-directory-sync.default-agent-health-score";
    public static final String CORE_FORWARD_ENABLED = "gateway.core-forward.enabled";
    public static final String CORE_FORWARD_HISTORY = "gateway.core-forward.history-limit";
    public static final String RECORD_BUSINESS = "gateway.core-forward.record-business-events";
    public static final String RECORD_TASK = "gateway.core-forward.record-task-lifecycle-events";
    public static final String RECORD_TRANSPORT = "gateway.core-forward.record-transport-signals";
    public static final String RECORD_HEARTBEAT = "gateway.core-forward.record-heartbeat-signals";
    public static final String RECORD_SYSTEM = "gateway.core-forward.record-system-signals";
    public static final String FORWARD_BUSINESS = "gateway.core-forward.forward-business-events";
    public static final String FORWARD_TASK = "gateway.core-forward.forward-task-lifecycle-events";
    public static final String FORWARD_TRANSPORT = "gateway.core-forward.forward-transport-signals";
    public static final String FORWARD_HEARTBEAT = "gateway.core-forward.forward-heartbeat-signals";
    public static final String FORWARD_SYSTEM = "gateway.core-forward.forward-system-signals";
    public static final String CORE_OUTBOUND_ENABLED = "gateway.core-outbound.enabled";
    public static final String CALLBACK_ENRICH_IDENTITY = "gateway.core-task-callback-relay.enrich-gateway-identity";
    public static final String CALLBACK_FILL_SESSION = "gateway.core-task-callback-relay.fill-missing-agent-session-id";
    public static final String CALLBACK_SYNC_TERMINAL = "gateway.core-task-callback-relay.synchronous-terminal-callbacks";
    public static final Set<String> ALL = Set.of(
            AGENT_AUTH_TIMEOUT, AGENT_AUTH_REJECTED_HISTORY,
            DIRECTORY_HEARTBEAT_INTERVAL, DIRECTORY_SNAPSHOT_INTERVAL, DIRECTORY_GATEWAY_LEASE_TTL,
            DIRECTORY_AGENT_LEASE_TTL, DIRECTORY_DEFAULT_AGENT_MAX_TASKS, DIRECTORY_DEFAULT_AGENT_HEALTH,
            CORE_FORWARD_ENABLED, CORE_FORWARD_HISTORY,
            RECORD_BUSINESS, RECORD_TASK, RECORD_TRANSPORT, RECORD_HEARTBEAT, RECORD_SYSTEM,
            FORWARD_BUSINESS, FORWARD_TASK, FORWARD_TRANSPORT, FORWARD_HEARTBEAT, FORWARD_SYSTEM,
            CORE_OUTBOUND_ENABLED, CALLBACK_ENRICH_IDENTITY, CALLBACK_FILL_SESSION, CALLBACK_SYNC_TERMINAL);

    private final CoreAgentAuthorizationProperties agentAuthorization;
    private final CoreDirectorySyncProperties directory;
    private final CoreForwardProperties forward;
    private final CoreOutboundProperties outbound;
    private final CoreTaskCallbackRelayProperties callback;
    private final GatewayRuntimeConfigurationSnapshotValues values;

    @Autowired
    public GatewayOperationalRuntimeConfigurationView(
            CoreAgentAuthorizationProperties agentAuthorization,
            CoreDirectorySyncProperties directory,
            CoreForwardProperties forward,
            CoreOutboundProperties outbound,
            CoreTaskCallbackRelayProperties callback,
            GatewayRuntimeConfigurationSnapshotValues values) {
        this.agentAuthorization = agentAuthorization;
        this.directory = directory;
        this.forward = forward;
        this.outbound = outbound;
        this.callback = callback;
        this.values = values;
    }

    public GatewayOperationalRuntimeConfigurationView(
            CoreAgentAuthorizationProperties agentAuthorization,
            CoreDirectorySyncProperties directory,
            CoreForwardProperties forward,
            CoreOutboundProperties outbound,
            CoreTaskCallbackRelayProperties callback) {
        this(agentAuthorization, directory, forward, outbound, callback, null);
    }

    public long agentAuthorizationTimeoutMs() { return bounded(AGENT_AUTH_TIMEOUT, num(AGENT_AUTH_TIMEOUT, agentAuthorization.timeoutMs()), 100, 120_000); }
    public int rejectedHistoryLimit() { return (int) bounded(AGENT_AUTH_REJECTED_HISTORY, num(AGENT_AUTH_REJECTED_HISTORY, agentAuthorization.rejectedHistoryLimit()), 10, 100_000); }
    public Duration directoryHeartbeatDelay() { return Duration.ofMillis(bounded(DIRECTORY_HEARTBEAT_INTERVAL, num(DIRECTORY_HEARTBEAT_INTERVAL, directory.gatewayHeartbeatIntervalMs()), 1_000, 3_600_000)); }
    public Duration directorySnapshotDelay() { return Duration.ofMillis(bounded(DIRECTORY_SNAPSHOT_INTERVAL, num(DIRECTORY_SNAPSHOT_INTERVAL, directory.snapshotIntervalMs()), 1_000, 86_400_000)); }
    public long gatewayLeaseTtlSeconds() { return bounded(DIRECTORY_GATEWAY_LEASE_TTL, num(DIRECTORY_GATEWAY_LEASE_TTL, directory.gatewayLeaseTtlSeconds()), 5, 86_400); }
    public long agentLeaseTtlSeconds() { return bounded(DIRECTORY_AGENT_LEASE_TTL, num(DIRECTORY_AGENT_LEASE_TTL, directory.agentLeaseTtlSeconds()), 5, 86_400); }
    public int defaultAgentMaxConcurrentTasks() { return (int) bounded(DIRECTORY_DEFAULT_AGENT_MAX_TASKS, num(DIRECTORY_DEFAULT_AGENT_MAX_TASKS, directory.defaultAgentMaxConcurrentTasks()), 1, 1_000); }
    public int defaultAgentHealthScore() { return (int) bounded(DIRECTORY_DEFAULT_AGENT_HEALTH, num(DIRECTORY_DEFAULT_AGENT_HEALTH, directory.defaultAgentHealthScore()), 1, 100); }
    public boolean coreForwardEnabled() { return bool(CORE_FORWARD_ENABLED, forward.enabled()); }
    public int coreForwardHistoryLimit() { return (int) bounded(CORE_FORWARD_HISTORY, num(CORE_FORWARD_HISTORY, forward.historyLimit()), 1, 100_000); }
    public boolean coreOutboundEnabled() { return bool(CORE_OUTBOUND_ENABLED, outbound.enabled()); }
    public boolean callbackEnrichGatewayIdentity() { return bool(CALLBACK_ENRICH_IDENTITY, callback.enrichGatewayIdentity()); }
    public boolean callbackFillMissingAgentSessionId() { return bool(CALLBACK_FILL_SESSION, callback.fillMissingAgentSessionId()); }
    public boolean callbackSynchronousTerminalCallbacks() { return bool(CALLBACK_SYNC_TERMINAL, callback.synchronousTerminalCallbacks()); }

    public boolean shouldRecord(InboundEventCategory category) {
        return switch (category == null ? InboundEventCategory.SYSTEM_SIGNAL : category) {
            case BUSINESS_EVENT -> bool(RECORD_BUSINESS, forward.recordBusinessEvents());
            case TASK_LIFECYCLE_EVENT -> bool(RECORD_TASK, forward.recordTaskLifecycleEvents());
            case TRANSPORT_SIGNAL -> bool(RECORD_TRANSPORT, forward.recordTransportSignals());
            case HEARTBEAT_SIGNAL -> bool(RECORD_HEARTBEAT, forward.recordHeartbeatSignals());
            case SYSTEM_SIGNAL -> bool(RECORD_SYSTEM, forward.recordSystemSignals());
        };
    }

    public boolean shouldForward(InboundEventCategory category) {
        return switch (category == null ? InboundEventCategory.SYSTEM_SIGNAL : category) {
            case BUSINESS_EVENT -> bool(FORWARD_BUSINESS, forward.forwardBusinessEvents());
            case TASK_LIFECYCLE_EVENT -> bool(FORWARD_TASK, forward.forwardTaskLifecycleEvents());
            case TRANSPORT_SIGNAL -> bool(FORWARD_TRANSPORT, forward.forwardTransportSignals());
            case HEARTBEAT_SIGNAL -> bool(FORWARD_HEARTBEAT, forward.forwardHeartbeatSignals());
            case SYSTEM_SIGNAL -> bool(FORWARD_SYSTEM, forward.forwardSystemSignals());
        };
    }

    private boolean bool(String key, boolean fallback) { return values == null ? fallback : values.booleanValueOrStartup(SET_KEY, key, fallback); }
    private long num(String key, long fallback) { return values == null ? fallback : values.longValueOrStartup(SET_KEY, key, fallback); }
    private static long bounded(String key, long value, long min, long max) {
        if (value < min || value > max) throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key + " value=" + value);
        return value;
    }
}
