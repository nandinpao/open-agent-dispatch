package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

public record AdapterActionRequestedEvent(
        String eventId,
        String actionId,
        String taskId,
        String incidentId,
        String adapterType,
        String actionType,
        String actionStatus,
        String idempotencyKey,
        OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "adapter-action.requested.v1";
    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "ADAPTER_ACTION"; }
    @Override public String aggregateId() { return actionId; }
}
