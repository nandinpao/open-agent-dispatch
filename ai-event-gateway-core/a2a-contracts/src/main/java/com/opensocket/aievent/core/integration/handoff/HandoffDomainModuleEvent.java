package com.opensocket.aievent.core.integration.handoff;

import java.time.OffsetDateTime;
import java.util.Map;

import com.opensocket.aievent.core.events.ModuleEvent;

/** Durable, provider-neutral Handoff event carried by the module outbox. */
public record HandoffDomainModuleEvent(
        String eventId,
        HandoffDomainEventType domainEventType,
        String tenantId,
        String snapshotId,
        String rootTaskId,
        String sourceTaskId,
        String targetTaskId,
        String correlationId,
        String causationId,
        String actorType,
        String actorId,
        OffsetDateTime occurredAt,
        Map<String, Object> payload) implements ModuleEvent {

    public static final String TYPE = "handoff.domain.v1";

    public HandoffDomainModuleEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "HANDOFF_CONTEXT_SNAPSHOT"; }
    @Override public String aggregateId() { return snapshotId; }
    @Override public String payloadVersion() { return "1"; }
}
