package com.opensocket.aievent.core.a2a;

/** Unified Phase 2H reconciliation lifecycle. OPEN is retained for migration compatibility. */
public enum A2AReconciliationStatus {
    OPEN,
    READY,
    CLAIMED,
    EXECUTING,
    RETRY_WAITING,
    WAIT_HUMAN,
    RESOLVED,
    IGNORED;

    public boolean active() {
        return this == OPEN || this == READY || this == CLAIMED || this == EXECUTING
                || this == RETRY_WAITING || this == WAIT_HUMAN;
    }

    public boolean terminal() { return this == RESOLVED || this == IGNORED; }
}
