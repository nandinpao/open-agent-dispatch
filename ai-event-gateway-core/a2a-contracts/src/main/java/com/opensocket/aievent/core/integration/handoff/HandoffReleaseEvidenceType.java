package com.opensocket.aievent.core.integration.handoff;

public enum HandoffReleaseEvidenceType {
    SNAPSHOT_READY,
    RELEASE_REQUESTED,
    RELEASED,
    RELEASE_FAILED,
    RECONCILED,
    EXPIRED,
    BINDING_CHANGED,
    HASH_CONFLICT
}
