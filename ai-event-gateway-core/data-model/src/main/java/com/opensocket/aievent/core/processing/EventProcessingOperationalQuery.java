package com.opensocket.aievent.core.processing;

/** Operational store metadata owned by Event Processing. */
public interface EventProcessingOperationalQuery {
    String dedupStoreMode();
    String dedupSnapshotStoreMode();
}
