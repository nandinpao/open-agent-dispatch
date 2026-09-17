package com.opensocket.aievent.core.issue.provider;

/** Projection lifecycle from immutable provider evidence into TaskIssueLink. */
public enum IssueLinkProjectionStatus {
    PENDING,
    PROJECTED,
    RETRY_WAITING,
    FAILED_PERMANENT,
    NOT_REQUIRED
}
