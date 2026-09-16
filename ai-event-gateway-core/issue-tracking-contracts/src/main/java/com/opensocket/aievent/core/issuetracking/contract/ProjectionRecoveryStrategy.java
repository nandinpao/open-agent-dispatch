package com.opensocket.aievent.core.issuetracking.contract;

/** Governed recovery choice for a failed Projection. */
public enum ProjectionRecoveryStrategy {
    NONE,
    RETRY,
    REFRESH_MAPPING,
    REVALIDATE_PRINCIPAL,
    VERIFY_EXTERNAL_RESULT,
    MANUAL_REVIEW,
    DEAD_LETTER,
    SUPERSEDE,
    DISABLE
}
