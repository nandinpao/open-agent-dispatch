package com.opensocket.aievent.core.issuetracking.contract;

/** Provider-neutral lifecycle of one external Issue Projection aggregate. */
public enum ProjectionLifecycleStatus {
    REQUESTED,
    RESOLVING_MAPPING,
    VALIDATING_PRINCIPAL,
    READY,
    IN_PROGRESS,
    VERIFYING_EXTERNAL_RESULT,
    SYNCED,
    FAILED,
    CONFLICT,
    DEAD_LETTER,
    SUPERSEDED,
    DISABLED
}
