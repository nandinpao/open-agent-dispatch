package com.opensocket.aievent.core.task.journey;

import java.time.OffsetDateTime;
import java.util.Map;

/** Pointer to authoritative evidence. It intentionally carries identifiers, not copied evidence payloads. */
public record TaskExecutionEvidenceRef(
        String evidenceType,
        String evidenceId,
        String authority,
        OffsetDateTime observedAt,
        Map<String, String> attributes) {
    public TaskExecutionEvidenceRef {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
