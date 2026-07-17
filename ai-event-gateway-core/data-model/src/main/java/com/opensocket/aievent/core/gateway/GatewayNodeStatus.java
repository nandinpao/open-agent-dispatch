package com.opensocket.aievent.core.gateway;

public enum GatewayNodeStatus {
    ONLINE,
    DEGRADED,
    DRAINING,
    OFFLINE,
    EXPIRED,
    ERROR;

    public boolean isAvailable() {
        return this == ONLINE || this == DEGRADED;
    }
}
