package com.opensocket.aievent.core.dedup.snapshot;

import java.time.Duration;

import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.event.NormalizedEvent;

public interface DedupStateSnapshotRepository {
    void saveSnapshot(DedupState state, NormalizedEvent event, Duration ttl);
    String mode();
}
