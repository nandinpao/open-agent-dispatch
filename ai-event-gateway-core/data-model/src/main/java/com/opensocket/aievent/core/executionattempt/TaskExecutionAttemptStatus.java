package com.opensocket.aievent.core.executionattempt;

public enum TaskExecutionAttemptStatus {
    CREATED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    STALE_CALLBACK_REJECTED
}
