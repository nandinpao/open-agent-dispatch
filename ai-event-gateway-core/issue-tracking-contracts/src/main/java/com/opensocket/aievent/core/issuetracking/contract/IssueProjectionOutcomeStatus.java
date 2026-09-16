package com.opensocket.aievent.core.issuetracking.contract;

/** Normalized provider outcome. It never carries Task or A2A mutation instructions. */
public enum IssueProjectionOutcomeStatus {
    SYNCED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    RESULT_UNCERTAIN,
    CONFLICT
}
