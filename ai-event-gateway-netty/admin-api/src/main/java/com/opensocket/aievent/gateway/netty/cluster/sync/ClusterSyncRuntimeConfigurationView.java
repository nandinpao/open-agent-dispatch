package com.opensocket.aievent.gateway.netty.cluster.sync;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.ClusterSyncProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

/** C3R2J typed local-snapshot view for active cluster state-sync controls. */
@Component
public final class ClusterSyncRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/CLUSTER/SYSTEM";
    public static final String ENABLED = "cluster.sync.enabled";
    public static final String INTERVAL_MS = "cluster.sync.interval-ms";
    public static final String REQUEST_TIMEOUT_MS = "cluster.sync.request-timeout-ms";
    public static final String REMOTE_STATE_TTL_MS = "cluster.sync.remote-state-ttl-ms";
    public static final String MAX_AGENTS_PER_NODE = "cluster.sync.max-agents-per-node";
    public static final String MAX_EVENTS_PER_NODE = "cluster.sync.max-events-per-node";
    public static final Set<String> ALL = Set.of(
            ENABLED, INTERVAL_MS, REQUEST_TIMEOUT_MS, REMOTE_STATE_TTL_MS,
            MAX_AGENTS_PER_NODE, MAX_EVENTS_PER_NODE);

    private final ClusterSyncProperties startup;
    private final GatewayRuntimeConfigurationSnapshotValues values;

    @Autowired
    public ClusterSyncRuntimeConfigurationView(
            ClusterSyncProperties startup,
            GatewayRuntimeConfigurationSnapshotValues values) {
        this.startup = startup;
        this.values = values;
    }

    /** Startup-only compatibility constructor for focused tests. */
    public ClusterSyncRuntimeConfigurationView(ClusterSyncProperties startup) {
        this.startup = startup;
        this.values = null;
    }

    public boolean enabled() { return bool(ENABLED, startup.enabled()); }
    public long intervalMs() { return bounded(INTERVAL_MS, number(INTERVAL_MS, startup.safeIntervalMs()), 250, 3_600_000); }
    public long requestTimeoutMs() { return bounded(REQUEST_TIMEOUT_MS, number(REQUEST_TIMEOUT_MS, startup.safeRequestTimeoutMs()), 100, 30_000); }
    public long remoteStateTtlMs() { return bounded(REMOTE_STATE_TTL_MS, number(REMOTE_STATE_TTL_MS, startup.safeRemoteStateTtlMs()), 1_000, 86_400_000); }
    public int maxAgentsPerNode() { return (int) bounded(MAX_AGENTS_PER_NODE, number(MAX_AGENTS_PER_NODE, startup.safeMaxAgentsPerNode()), 1, 100_000); }
    public int maxEventsPerNode() { return (int) bounded(MAX_EVENTS_PER_NODE, number(MAX_EVENTS_PER_NODE, startup.safeMaxEventsPerNode()), 1, 100_000); }
    public Duration interval() { return Duration.ofMillis(intervalMs()); }

    public boolean snapshotPresent() { return values != null && values.hasSnapshot(SET_KEY); }

    private boolean bool(String key, boolean fallback) {
        return values == null ? fallback : values.booleanValueOrStartup(SET_KEY, key, fallback);
    }
    private long number(String key, long fallback) {
        return values == null ? fallback : values.longValueOrStartup(SET_KEY, key, fallback);
    }
    private static long bounded(String key, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key + " value=" + value);
        }
        return value;
    }
}
