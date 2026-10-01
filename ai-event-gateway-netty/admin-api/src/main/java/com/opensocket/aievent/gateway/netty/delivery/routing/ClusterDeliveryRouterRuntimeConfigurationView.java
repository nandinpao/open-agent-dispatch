package com.opensocket.aievent.gateway.netty.delivery.routing;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.config.DeliveryRouterProperties;
import com.opensocket.aievent.gateway.netty.configuration.GatewayRuntimeConfigurationSnapshotValues;

/** C3R2J typed local-snapshot view for cluster one-hop delivery routing policy. */
@Component
public final class ClusterDeliveryRouterRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/CLUSTER/SYSTEM";
    public static final String ENABLED = "cluster.delivery-router.enabled";
    public static final String PREFER_LOCAL = "cluster.delivery-router.prefer-local";
    public static final String REJECT_DUPLICATE_AGENTS = "cluster.delivery-router.reject-duplicate-agents";
    public static final String REQUIRE_SYNCED_REMOTE_STATE = "cluster.delivery-router.require-synced-remote-state";
    public static final String REQUEST_TIMEOUT_MS = "cluster.delivery-router.request-timeout-ms";
    public static final Set<String> ALL = Set.of(
            ENABLED, PREFER_LOCAL, REJECT_DUPLICATE_AGENTS,
            REQUIRE_SYNCED_REMOTE_STATE, REQUEST_TIMEOUT_MS);

    private final DeliveryRouterProperties startup;
    private final GatewayRuntimeConfigurationSnapshotValues values;

    @Autowired
    public ClusterDeliveryRouterRuntimeConfigurationView(
            DeliveryRouterProperties startup,
            GatewayRuntimeConfigurationSnapshotValues values) {
        this.startup = startup;
        this.values = values;
    }

    /** Startup-only compatibility constructor for focused tests. */
    public ClusterDeliveryRouterRuntimeConfigurationView(DeliveryRouterProperties startup) {
        this.startup = startup;
        this.values = null;
    }

    public boolean enabled() { return bool(ENABLED, startup.enabled()); }
    public boolean preferLocal() { return bool(PREFER_LOCAL, startup.safePreferLocal()); }
    public boolean rejectDuplicateAgents() { return bool(REJECT_DUPLICATE_AGENTS, startup.safeRejectDuplicateAgents()); }
    public boolean requireSyncedRemoteState() { return bool(REQUIRE_SYNCED_REMOTE_STATE, startup.safeRequireSyncedRemoteState()); }
    public long requestTimeoutMs() { return bounded(REQUEST_TIMEOUT_MS, number(REQUEST_TIMEOUT_MS, startup.safeRequestTimeoutMs()), 100, 30_000); }

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
