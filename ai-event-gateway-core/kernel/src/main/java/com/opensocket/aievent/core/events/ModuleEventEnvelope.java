package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

/**
 * Canonical metadata envelope for every transactional module event.
 *
 * <p>The payload remains event-specific. This envelope is persisted separately by the module
 * outbox so correlation/causation/actor evidence can be queried without deserializing arbitrary
 * payload JSON. New events must propagate correlation from the originating business journey.
 * Legacy events that do not yet expose correlation are explicitly marked as fallback lineage.</p>
 */
public record ModuleEventEnvelope(
        String eventId,
        String eventType,
        String payloadVersion,
        String tenantId,
        String aggregateType,
        String aggregateId,
        String rootTaskId,
        String taskId,
        String correlationId,
        String causationId,
        String traceId,
        String spanId,
        String actorType,
        String actorId,
        String lineageStatus,
        OffsetDateTime occurredAt) {

    public static final String PROPAGATED = "PROPAGATED";
    public static final String LEGACY_CORRELATION_FALLBACK = "LEGACY_CORRELATION_FALLBACK";

    public static ModuleEventEnvelope from(ModuleEvent event) {
        if (event == null) throw new IllegalArgumentException("module event is required");
        String correlation = normalize(event.correlationId());
        String lineage = PROPAGATED;
        if (correlation == null) {
            // Compatibility only. Phase 3 makes this fallback visible instead of silently pretending
            // that a newly-created eventId is an upstream business correlation identifier.
            correlation = event.eventId();
            lineage = LEGACY_CORRELATION_FALLBACK;
        }
        return new ModuleEventEnvelope(
                required(event.eventId(), "eventId"),
                required(event.eventType(), "eventType"),
                defaultValue(event.payloadVersion(), "1"),
                normalize(event.tenantId()),
                required(event.aggregateType(), "aggregateType"),
                required(event.aggregateId(), "aggregateId"),
                normalize(event.rootTaskId()),
                normalize(event.taskId()),
                required(correlation, "correlationId"),
                normalize(event.causationId()),
                normalize(event.traceId()),
                normalize(event.spanId()),
                defaultValue(event.actorType(), "SYSTEM"),
                defaultValue(event.actorId(), "opendispatch"),
                lineage,
                event.occurredAt());
    }

    private static String required(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
    private static String defaultValue(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }
    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
