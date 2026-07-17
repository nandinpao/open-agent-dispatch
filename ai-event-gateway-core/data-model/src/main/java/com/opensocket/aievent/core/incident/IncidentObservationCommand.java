package com.opensocket.aievent.core.incident;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.opensocket.aievent.core.event.NormalizedEvent;

public record IncidentObservationCommand(
        String fingerprint,
        NormalizedEvent event,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        long occurrenceCount) {

    public IncidentObservationCommand {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (occurrenceCount < 1) {
            throw new IllegalArgumentException("occurrenceCount must be at least 1");
        }
    }
}
