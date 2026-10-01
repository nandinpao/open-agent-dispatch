package com.opensocket.aievent.gateway.netty.configuration;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Deterministic freshness policy shared by reads, cold-start recovery, and health reporting. */
public final class GatewayRuntimeConfigurationSnapshotFreshness {
    private GatewayRuntimeConfigurationSnapshotFreshness() {}

    public static GatewayRuntimeConfigurationSnapshotState state(
            GatewayRuntimeConfigurationSnapshot snapshot,
            long maxStaleMs) {
        return state(snapshot, maxStaleMs, OffsetDateTime.now(ZoneOffset.UTC));
    }

    static GatewayRuntimeConfigurationSnapshotState state(
            GatewayRuntimeConfigurationSnapshot snapshot,
            long maxStaleMs,
            OffsetDateTime now) {
        if (snapshot == null) return GatewayRuntimeConfigurationSnapshotState.MISSING;
        if (snapshot.expiresAt() == null) return GatewayRuntimeConfigurationSnapshotState.INVALID;
        if (snapshot.expiresAt().isAfter(now)) return GatewayRuntimeConfigurationSnapshotState.ACTIVE;
        long bounded = Math.max(0L, maxStaleMs);
        if (bounded == 0L) return GatewayRuntimeConfigurationSnapshotState.EXPIRED;
        OffsetDateTime staleUntil = snapshot.expiresAt().plus(Duration.ofMillis(bounded));
        return staleUntil.isAfter(now)
                ? GatewayRuntimeConfigurationSnapshotState.STALE_LKG
                : GatewayRuntimeConfigurationSnapshotState.EXPIRED;
    }
}
