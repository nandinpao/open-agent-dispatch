package com.opensocket.aievent.core.dedup.cache;

import java.time.Duration;

import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.event.NormalizedEvent;

public interface DedupStateCache {
    void publish(DedupState state, NormalizedEvent event, Duration ttl);
    String mode();
}
