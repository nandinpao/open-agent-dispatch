package com.opensocket.aievent.core.action;

public enum AdapterActionStatus {
    PENDING,
    CLAIMED,
    EXECUTING,
    RETRY_WAITING,
    EXECUTOR_UNAVAILABLE,
    SUPPRESSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
