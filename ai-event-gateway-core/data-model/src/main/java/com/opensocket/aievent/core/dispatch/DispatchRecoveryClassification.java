package com.opensocket.aievent.core.dispatch;

/** Typed durable recovery reason; never infer repair from lastError text. */
public enum DispatchRecoveryClassification {
    NONE,
    RESPONSE_LOST,
    ASSIGNMENT_STATE_UNCERTAIN,
    ACK_PERSISTENCE_UNCERTAIN,
    LEASE_EXPIRED,
    RETRY_EXHAUSTED,
    POLICY_OR_PERMISSION_BLOCKED,
    INFRASTRUCTURE_RETRY,
    REASSIGN_REQUIRED,
    POLICY_BLOCKED,
    SECURITY_BLOCKED,
    TERMINAL_FAILURE
}
