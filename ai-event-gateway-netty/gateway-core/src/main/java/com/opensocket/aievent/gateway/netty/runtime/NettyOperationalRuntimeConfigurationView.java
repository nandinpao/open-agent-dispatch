package com.opensocket.aievent.gateway.netty.runtime;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.ClusterRuntimeProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

/** C3R2I typed runtime view for active Netty operational timing and history controls. */
@Component
public final class NettyOperationalRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/NETTY/SYSTEM";
    public static final String CLUSTER_HEARTBEAT = "netty.cluster.heartbeat-interval-ms";
    public static final String CLUSTER_SUSPECT = "netty.cluster.suspect-timeout-ms";
    public static final String CLUSTER_OFFLINE = "netty.cluster.offline-timeout-ms";
    public static final String TCP_CLEANUP_ENABLED = "netty.tcp.closed-connection-cleanup-enabled";
    public static final String TCP_CLEANUP_INTERVAL = "netty.tcp.closed-connection-cleanup-interval-ms";
    public static final String TCP_HISTORY_TTL = "netty.tcp.closed-connection-history-ttl-ms";
    public static final String TCP_HISTORY_MAX = "netty.tcp.max-closed-connection-history";
    public static final Set<String> ALL = Set.of(
            CLUSTER_HEARTBEAT, CLUSTER_SUSPECT, CLUSTER_OFFLINE,
            TCP_CLEANUP_ENABLED, TCP_CLEANUP_INTERVAL,
            TCP_HISTORY_TTL, TCP_HISTORY_MAX);

    private final ClusterRuntimeProperties clusterStartup;
    private final GatewayRuntimeConfigurationSnapshotValues values;
    private final Environment environment;

    @Autowired
    public NettyOperationalRuntimeConfigurationView(
            ClusterRuntimeProperties clusterStartup,
            GatewayRuntimeConfigurationSnapshotValues values,
            Environment environment) {
        this.clusterStartup = clusterStartup;
        this.values = values;
        this.environment = environment;
    }

    public long clusterHeartbeatIntervalMs() { return bounded(CLUSTER_HEARTBEAT, number(CLUSTER_HEARTBEAT, clusterStartup.heartbeatIntervalMs()), 250, 3_600_000); }
    public long clusterSuspectTimeoutMs() { return bounded(CLUSTER_SUSPECT, number(CLUSTER_SUSPECT, clusterStartup.suspectTimeoutMs()), 500, 86_400_000); }
    public long clusterOfflineTimeoutMs() { return bounded(CLUSTER_OFFLINE, number(CLUSTER_OFFLINE, clusterStartup.offlineTimeoutMs()), 1_000, 172_800_000); }
    public Duration clusterHeartbeatDelay() { return Duration.ofMillis(clusterHeartbeatIntervalMs()); }

    public boolean tcpCleanupEnabled() { return bool(TCP_CLEANUP_ENABLED, envBool(TCP_CLEANUP_ENABLED, true)); }
    public long tcpCleanupInitialDelayMs() { return bounded("netty.tcp.closed-connection-cleanup-initial-delay-ms", envLong("netty.tcp.closed-connection-cleanup-initial-delay-ms", 60_000L), 100, 86_400_000); }
    public long tcpCleanupIntervalMs() { return bounded(TCP_CLEANUP_INTERVAL, number(TCP_CLEANUP_INTERVAL, envLong(TCP_CLEANUP_INTERVAL, 60_000L)), 100, 86_400_000); }
    public long tcpClosedHistoryTtlMs() { return bounded(TCP_HISTORY_TTL, number(TCP_HISTORY_TTL, envLong(TCP_HISTORY_TTL, 600_000L)), 0, 604_800_000); }
    public int tcpMaxClosedHistory() {
        long v = bounded(TCP_HISTORY_MAX, number(TCP_HISTORY_MAX, envLong(TCP_HISTORY_MAX, 2_000L)), 0, 1_000_000);
        return (int) v;
    }
    public Duration tcpCleanupInitialDelay() { return Duration.ofMillis(tcpCleanupInitialDelayMs()); }
    public Duration tcpCleanupInterval() { return Duration.ofMillis(tcpCleanupIntervalMs()); }

    private boolean bool(String key, boolean fallback) { return values == null ? fallback : values.booleanValueOrStartup(SET_KEY, key, fallback); }
    private long number(String key, long fallback) { return values == null ? fallback : values.longValueOrStartup(SET_KEY, key, fallback); }
    private long envLong(String key, long fallback) { return environment == null ? fallback : environment.getProperty(key, Long.class, fallback); }
    private boolean envBool(String key, boolean fallback) { return environment == null ? fallback : environment.getProperty(key, Boolean.class, fallback); }
    private static long bounded(String key, long value, long min, long max) {
        if (value < min || value > max) throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key + " value=" + value);
        return value;
    }
}
