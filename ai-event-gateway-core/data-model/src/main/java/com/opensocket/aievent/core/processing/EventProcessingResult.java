package com.opensocket.aievent.core.processing;

import java.util.Objects;

import com.opensocket.aievent.core.dedup.DedupDecision;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.core.incident.Incident;

public record EventProcessingResult(
        NormalizedEvent event,
        String fingerprint,
        DedupDecision dedup,
        Incident incident) {

    public EventProcessingResult {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(dedup, "dedup");
        Objects.requireNonNull(incident, "incident");
    }
}
