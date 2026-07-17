package com.opensocket.aievent.core.dedup.snapshot;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
@ConditionalOnProperty(prefix = "event.dedup", name = "snapshot-store", havingValue = "NONE", matchIfMissing = true)
public class NoopDedupStateSnapshotRepository implements DedupStateSnapshotRepository {
    @Override
    public void saveSnapshot(DedupState state, NormalizedEvent event, Duration ttl) {
        // No persistent dedup snapshot in MEMORY/NONE mode.
    }

    @Override
    public String mode() {
        return "NONE";
    }
}
