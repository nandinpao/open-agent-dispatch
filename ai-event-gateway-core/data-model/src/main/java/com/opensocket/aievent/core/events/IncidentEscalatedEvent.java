package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

public record IncidentEscalatedEvent(
        String eventId,
        String incidentId,
        String fingerprint,
        String severity,
        long occurrenceCount,
        String sourceEventId,
        String tenantId,
        String siteId,
        OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "incident.escalated.v1";
    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "INCIDENT"; }
    @Override public String aggregateId() { return incidentId; }
}
