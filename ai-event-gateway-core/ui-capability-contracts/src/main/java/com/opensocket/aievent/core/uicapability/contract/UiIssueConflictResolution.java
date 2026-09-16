package com.opensocket.aievent.core.uicapability.contract;

/** User-facing resolution intent; OpenDispatch remains the Task authority. */
public enum UiIssueConflictResolution {
    OPENDISPATCH_WINS,
    EXTERNAL_DISPLAY_ONLY,
    MERGE_COMMENTS,
    CREATE_REPLACEMENT,
    MANUAL_REVIEW,
    IGNORE
}
