package com.opensocket.aievent.core.integration.handoff;

/** Durable release lifecycle for an immutable Handoff Snapshot. */
public enum HandoffSnapshotReleaseStatus {
    WAITING_APPROVAL,
    READY,
    RELEASING,
    RELEASED,
    FAILED_RETRYABLE,
    WAIT_HUMAN
}
