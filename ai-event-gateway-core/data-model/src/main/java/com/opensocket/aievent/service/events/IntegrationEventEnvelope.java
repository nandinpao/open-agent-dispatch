package com.opensocket.aievent.service.events;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable envelope exposed beyond the modular-monolith process boundary.
 */
public record IntegrationEventEnvelope(
        String specVersion,
        String eventId,
        String eventType,
        String source,
        String aggregateType,
        String aggregateId,
        OffsetDateTime occurredAt,
        Map<String, Object> payload,
        Map<String, String> metadata) {

    public IntegrationEventEnvelope {
        specVersion = specVersion == null || specVersion.isBlank() ? "1.0" : specVersion;
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
