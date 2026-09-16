package com.opensocket.aievent.core.uicapability.contract;

/** Safe UI projection of external issue synchronization queue state. */
public enum UiIssueSyncQueueStatus {
    PENDING,
    RETRYING,
    WAITING_FOR_INDEX,
    FAILED,
    DEAD_LETTER,
    CONFLICT
}
