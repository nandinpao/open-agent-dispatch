package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;
import java.util.Map;

/** Durable representation of an A2A domain event for cross-module subscribers. */
public record A2ADomainModuleEvent(
        String eventId,
        String domainEventType,
        String tenantId,
        String a2aRequestId,
        String rootTaskId,
        String correlationId,
        String causationId,
        String actorType,
        String actorId,
        OffsetDateTime occurredAt,
        Map<String, Object> payload) implements ModuleEvent {

    public static final String TYPE = "a2a.domain.v1";

    public A2ADomainModuleEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "A2A_REQUEST"; }
    @Override public String aggregateId() { return a2aRequestId; }
    @Override public String payloadVersion() { return "1"; }
}
