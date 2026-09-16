package com.opensocket.aievent.core.issuetracking.contract;

/** Stable business purpose included in the unique Projection aggregate key. */
public enum ProjectionPurpose {
    PRIMARY_ISSUE,
    INCIDENT_ESCALATION,
    HANDOFF_AUDIT,
    RESULT_SUMMARY,
    OPERATIONAL_FOLLOW_UP
}
