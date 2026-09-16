package com.opensocket.aievent.core.a2a;

/** Precise operational classification for every non-canonical Result submission. */
public enum A2AResultClassification {
    IDENTICAL_DUPLICATE,
    CONFLICTING_DUPLICATE,
    STALE_ATTEMPT,
    LATE_RESULT,
    UNKNOWN_ASSIGNMENT,
    TOKEN_MISMATCH,
    TERMINAL_REQUEST,
    MISSING_EVIDENCE,
    BINDING_CONFLICT
}
