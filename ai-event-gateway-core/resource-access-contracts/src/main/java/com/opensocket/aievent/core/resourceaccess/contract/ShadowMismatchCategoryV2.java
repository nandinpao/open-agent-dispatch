package com.opensocket.aievent.core.resourceaccess.contract;
/** Phase 5I semantic mismatch taxonomy. Categories are ordered by security significance in the comparator. */
public enum ShadowMismatchCategoryV2 {
    MATCH,
    UNEXPECTED_ALLOW,
    SCOPE_WIDENED,
    VISIBILITY_WIDENED,
    TARGET_ERROR,
    CONTEXT_INCOMPLETE,
    UNEXPECTED_DENY,
    REASON_DIFFERENT,
    LEGACY_UNAVAILABLE
}
