package com.opensocket.aievent.core.dispatch;

/** Durable worker lifecycle independent from the business-facing dispatch request status. */
public enum DispatchOutboxStatus {
    PENDING,
    CLAIMED,
    DISPATCHING,
    ACKNOWLEDGED,
    FAILED_RETRYABLE,
    /** Delivery may already have occurred; hold for callback/reconciliation instead of blind retry. */
    RECOVERY_PENDING,
    /** Current dispatch is obsolete because Core must choose a new assignment/lease. */
    ABANDONED,
    /** Current dispatch is intentionally held until policy/security authority changes. */
    BLOCKED,
    DEAD_LETTER
}
