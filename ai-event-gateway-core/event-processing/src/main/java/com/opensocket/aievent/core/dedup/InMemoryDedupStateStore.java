package com.opensocket.aievent.core.dedup;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.event.NormalizedEvent;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "event.dedup", name = "store", havingValue = "MEMORY")
public class InMemoryDedupStateStore implements DedupStateStore {
    private final Map<String, TimedState> states = new ConcurrentHashMap<>();

    @Override
    public synchronized DedupDecision touch(String fingerprint, NormalizedEvent event, Duration duplicateWindow, Duration ttl) {
        OffsetDateTime now = event.occurredAt();
        TimedState existing = states.get(fingerprint);
        if (existing == null || existing.expiresAt().isBefore(now)) {
            DedupState newState = new DedupState(fingerprint, null, now, now, 1, event.severity(), event.eventId(), event.normalizedMessage());
            states.put(fingerprint, new TimedState(newState, now.plus(ttl)));
            return new DedupDecision(false, newState, "no active dedup state");
        }

        DedupState state = existing.state();
        boolean duplicate = !state.getLastSeenAt().plus(duplicateWindow).isBefore(now);
        state.setOccurrenceCount(state.getOccurrenceCount() + 1);
        state.setLastSeenAt(now);
        state.setLastEventId(event.eventId());
        state.setLastMessage(event.normalizedMessage());
        if (event.severity().higherThan(state.getMaxSeverity())) {
            state.setMaxSeverity(event.severity());
        }
        states.put(fingerprint, new TimedState(state, now.plus(ttl)));
        return new DedupDecision(duplicate, state, duplicate ? "same fingerprint within duplicate window" : "same fingerprint outside duplicate window");
    }

    @Override
    public synchronized void attachIncident(String fingerprint, String incidentId, Duration ttl) {
        TimedState existing = states.get(fingerprint);
        if (existing == null) {
            return;
        }
        existing.state().setActiveIncidentId(incidentId);
        states.put(fingerprint, new TimedState(existing.state(), OffsetDateTime.now().plus(ttl)));
    }

    @Override
    public Optional<DedupState> find(String fingerprint) {
        TimedState existing = states.get(fingerprint);
        if (existing == null || existing.expiresAt().isBefore(OffsetDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(existing.state());
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private record TimedState(DedupState state, OffsetDateTime expiresAt) {}
}
