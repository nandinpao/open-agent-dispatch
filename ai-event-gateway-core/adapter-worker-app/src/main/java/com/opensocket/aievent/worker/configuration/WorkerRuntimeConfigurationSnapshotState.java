package com.opensocket.aievent.worker.configuration;

/** Runtime freshness state for the last authenticated Worker configuration snapshot. */
public enum WorkerRuntimeConfigurationSnapshotState {
    ACTIVE,
    STALE_LKG,
    EXPIRED,
    INVALID,
    MISSING
}
