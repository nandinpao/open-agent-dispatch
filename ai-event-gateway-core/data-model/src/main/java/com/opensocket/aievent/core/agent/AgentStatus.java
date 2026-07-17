package com.opensocket.aievent.core.agent;

public enum AgentStatus {
    CONNECTED,
    IDLE,
    BUSY,
    BUSY_ACCEPTING,
    DRAINING,
    OFFLINE,
    EXPIRED,
    ERROR;

    public boolean isAssignable() {
        // CONNECTED is a transport/session status kept for backward compatibility
        // with older runtime snapshots.  AgentDirectoryService normalizes it to a
        // workload status before persistence, but treating it as capacity-gated
        // assignable prevents stale CONNECTED rows from being misreported as
        // missing Profile/Capability during dispatch recovery.
        return this == CONNECTED || this == IDLE || this == BUSY_ACCEPTING;
    }
}
