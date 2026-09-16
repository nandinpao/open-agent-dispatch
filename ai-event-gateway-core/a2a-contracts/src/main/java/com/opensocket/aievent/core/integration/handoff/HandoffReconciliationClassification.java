package com.opensocket.aievent.core.integration.handoff;

/** Deterministic reason used by the Handoff reconciler. */
public enum HandoffReconciliationClassification {
    NONE,
    RELEASE_EVENT_MISSING,
    APPROVAL_RELEASE_GAP,
    SNAPSHOT_EXPIRED,
    TARGET_BINDING_CHANGED,
    HASH_CONFLICT,
    RELEASE_PERSISTENCE_UNCERTAIN,
    RETRY_EXHAUSTED
}
