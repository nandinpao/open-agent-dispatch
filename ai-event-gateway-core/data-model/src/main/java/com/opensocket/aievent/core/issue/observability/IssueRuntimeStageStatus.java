package com.opensocket.aievent.core.issue.observability;

/** Status vocabulary shared by Core, Admin UI and diagnostics for Route B. */
public enum IssueRuntimeStageStatus {
    SUCCEEDED,
    NOT_REQUIRED,
    NOT_STARTED,
    PENDING,
    IN_PROGRESS,
    BLOCKED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    UNKNOWN;

    public boolean isProblem() {
        return this == BLOCKED || this == FAILED_RETRYABLE || this == FAILED_FINAL;
    }

    public boolean isTerminalSuccess() {
        return this == SUCCEEDED || this == NOT_REQUIRED;
    }
}
