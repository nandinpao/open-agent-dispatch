package com.opensocket.aievent.service.events;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned envelope exported beyond the modular-monolith boundary. */
public record IntegrationEventEnvelope(
        String specVersion,
        String eventId,
        String eventType,
        String source,
        String tenantId,
        String aggregateType,
        String aggregateId,
        String rootTaskId,
        String correlationId,
        String causationId,
        String actorType,
        String actorId,
        OffsetDateTime occurredAt,
        String payloadVersion,
        Map<String, Object> payload,
        Map<String, String> metadata) {

    public IntegrationEventEnvelope {
        specVersion = blank(specVersion) ? "1.0" : specVersion;
        payloadVersion = blank(payloadVersion) ? "1" : payloadVersion;
        rootTaskId = blank(rootTaskId) ? aggregateId : rootTaskId;
        correlationId = blank(correlationId) ? eventId : correlationId;
        actorType = blank(actorType) ? "SYSTEM" : actorType;
        actorId = blank(actorId) ? "opendispatch" : actorId;
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * Fails closed before an event crosses the modular-monolith boundary.
     * Causation may be null for a root event, but the property remains part of
     * the canonical envelope and is serialized explicitly.
     */
    public IntegrationEventEnvelope requireExportable() {
        require("specVersion", specVersion);
        require("eventId", eventId);
        require("eventType", eventType);
        require("source", source);
        require("tenantId", tenantId);
        require("aggregateType", aggregateType);
        require("aggregateId", aggregateId);
        require("rootTaskId", rootTaskId);
        require("correlationId", correlationId);
        require("actorType", actorType);
        require("actorId", actorId);
        require("payloadVersion", payloadVersion);
        if (occurredAt == null) {
            throw new IllegalArgumentException("EVENT_ENVELOPE_REQUIRED_FIELD_MISSING: occurredAt");
        }
        if (!"1.0".equals(specVersion)) {
            throw new IllegalArgumentException("EVENT_SCHEMA_VERSION_UNSUPPORTED: " + specVersion);
        }
        return this;
    }

    /** Compatibility constructor retained for existing sinks while producers migrate to the canonical envelope. */
    public IntegrationEventEnvelope(
            String specVersion,
            String eventId,
            String eventType,
            String source,
            String aggregateType,
            String aggregateId,
            OffsetDateTime occurredAt,
            Map<String, Object> payload,
            Map<String, String> metadata) {
        this(specVersion, eventId, eventType, source, null, aggregateType, aggregateId,
                null, null, null, "SYSTEM", "opendispatch", occurredAt, "1", payload, metadata);
    }

    private static void require(String field, String value) {
        if (blank(value)) {
            throw new IllegalArgumentException("EVENT_ENVELOPE_REQUIRED_FIELD_MISSING: " + field);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
