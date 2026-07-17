package com.opensocket.aievent.core.action.executor;

public enum AdapterExecutionOutcome {
    SUCCESS,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    EXECUTOR_UNAVAILABLE,
    TIMEOUT
}
