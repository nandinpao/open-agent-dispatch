package com.opensocket.aievent.core.a2a;

public enum A2ACancellationReconciliationClassification {
    NONE,
    RUNTIME_ACK_MISSING,
    DELIVERY_FAILED,
    CANCELLATION_TIMEOUT,
    AGENT_ALREADY_COMPLETED,
    STALE_CANCELLATION,
    FENCING_CONFLICT,
    PROCESS_CRASH,
    RETRY_EXHAUSTED
}
