package com.opensocket.aievent.core.dedup.cache;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
@ConditionalOnProperty(prefix = "event.dedup", name = "cache-store", havingValue = "NONE", matchIfMissing = true)
public class NoopDedupStateCache implements DedupStateCache {
    @Override
    public void publish(DedupState state, NormalizedEvent event, Duration ttl) {
        // no-op
    }

    @Override
    public String mode() {
        return "NONE";
    }
}
