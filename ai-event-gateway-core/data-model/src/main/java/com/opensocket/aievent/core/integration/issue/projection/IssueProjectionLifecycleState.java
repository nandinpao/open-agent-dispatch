package com.opensocket.aievent.core.integration.issue.projection;

import com.opensocket.aievent.core.issuetracking.contract.ProjectionLifecycleStatus;

/** @deprecated Phase 3D compatibility alias. Use ProjectionLifecycleStatus. */
@Deprecated(forRemoval = false)
public enum IssueProjectionLifecycleState {
    NOT_REQUESTED, PENDING, CREATING, ACTIVE, UPDATE_PENDING, FAILED, CONFLICT, SUSPENDED, DEAD_LETTER;
    public ProjectionLifecycleStatus canonical() {
        return switch (this) {
            case NOT_REQUESTED, PENDING -> ProjectionLifecycleStatus.REQUESTED;
            case CREATING -> ProjectionLifecycleStatus.IN_PROGRESS;
            case ACTIVE -> ProjectionLifecycleStatus.SYNCED;
            case UPDATE_PENDING -> ProjectionLifecycleStatus.READY;
            case FAILED -> ProjectionLifecycleStatus.FAILED;
            case CONFLICT -> ProjectionLifecycleStatus.CONFLICT;
            case SUSPENDED -> ProjectionLifecycleStatus.DISABLED;
            case DEAD_LETTER -> ProjectionLifecycleStatus.DEAD_LETTER;
        };
    }
}
