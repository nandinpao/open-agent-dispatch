package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for cluster-aware command delivery routing.
 *
 * <p>The router is intentionally lightweight: it uses the current cluster state-sync snapshots to
 * locate the gateway that owns an Agent connection, then forwards the same command to that
 * gateway's local-only internal delivery endpoint. It is not a business task dispatcher, retry
 * queue, or global persistent Agent directory.</p>
 */
@ConfigurationProperties(prefix = "cluster.delivery-router")
public record DeliveryRouterProperties(
        boolean enabled,
        boolean preferLocal,
        boolean rejectDuplicateAgents,
        boolean requireSyncedRemoteState,
        long requestTimeoutMs
) {
    public boolean safePreferLocal() {
        return preferLocal;
    }

    public boolean safeRejectDuplicateAgents() {
        return rejectDuplicateAgents;
    }

    public boolean safeRequireSyncedRemoteState() {
        return requireSyncedRemoteState;
    }

    public long safeRequestTimeoutMs() {
        return requestTimeoutMs <= 0 ? 3000L : Math.min(30000L, Math.max(100L, requestTimeoutMs));
    }
}
