package com.opensocket.aievent.core.dedup;

import java.time.Duration;
import java.util.Optional;

import com.opensocket.aievent.core.event.NormalizedEvent;

public interface DedupStateStore {
    DedupDecision touch(String fingerprint, NormalizedEvent event, Duration duplicateWindow, Duration ttl);
    void attachIncident(String fingerprint, String incidentId, Duration ttl);
    Optional<DedupState> find(String fingerprint);
    String mode();

    /**
     * True when this store is transactional with the relational event-processing
     * source of truth. Non-transactional stores such as Redis/Redisson must only
     * publish incident attachments after the surrounding database transaction
     * commits.
     */
    default boolean transactionalSourceOfTruth() {
        return false;
    }
}
