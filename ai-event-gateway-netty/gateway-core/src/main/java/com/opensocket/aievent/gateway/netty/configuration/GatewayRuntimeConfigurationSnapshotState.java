package com.opensocket.aievent.gateway.netty.configuration;

/** Runtime freshness state for the last authenticated Gateway configuration snapshot. */
public enum GatewayRuntimeConfigurationSnapshotState {
    ACTIVE,
    STALE_LKG,
    EXPIRED,
    INVALID,
    MISSING
}
