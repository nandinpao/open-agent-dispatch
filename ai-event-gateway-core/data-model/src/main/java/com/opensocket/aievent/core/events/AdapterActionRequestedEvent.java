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
        String tenantId,
        OffsetDateTime occurredAt,
        String correlationId,
        String causationId,
        String traceId,
        String spanId,
        String actorType,
        String actorId) implements ModuleEvent {
    public static final String TYPE = "adapter-action.requested.v1";

    /** Compatibility constructor for pre-Phase-3 publishers. */
    public AdapterActionRequestedEvent(
            String eventId, String actionId, String taskId, String incidentId, String adapterType,
            String actionType, String actionStatus, String idempotencyKey, String tenantId,
            OffsetDateTime occurredAt) {
        this(eventId,actionId,taskId,incidentId,adapterType,actionType,actionStatus,idempotencyKey,
                tenantId,occurredAt,null,null,null,null,null,null);
    }

    /** Compatibility constructor for older tests and local publishers. */
    public AdapterActionRequestedEvent(
            String eventId,
            String actionId,
            String taskId,
            String incidentId,
            String adapterType,
            String actionType,
            String actionStatus,
            String idempotencyKey,
            OffsetDateTime occurredAt) {
        this(eventId, actionId, taskId, incidentId, adapterType, actionType,
                actionStatus, idempotencyKey, null, occurredAt,null,null,null,null,null,null);
    }

    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "ADAPTER_ACTION"; }
    @Override public String aggregateId() { return actionId; }
    @Override public String rootTaskId() { return taskId; }
}
