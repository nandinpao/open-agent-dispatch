package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

/**
 * Transactional module-outbox command requesting Dispatch Authority to assign
 * and dispatch an A2A Child Task.
 */
public record A2ADispatchRequestedEvent(
        String eventId,
        String tenantId,
        String a2aRequestId,
        String childTaskId,
        String targetAgentPoolId,
        String idempotencyKey,
        String correlationId,
        OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "a2a.dispatch-requested.v1";

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateType() {
        return "A2A_REQUEST";
    }

    @Override
    public String aggregateId() {
        return a2aRequestId;
    }

    @Override
    public String rootTaskId() {
        return childTaskId;
    }
}
