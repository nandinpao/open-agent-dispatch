package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

/** Runtime cancellation intent. No raw dispatch/fencing secret is copied into the outbox payload. */
public record A2ARuntimeCancelRequestedEvent(
        String eventId,
        String tenantId,
        String cancellationId,
        String a2aRequestId,
        String childTaskId,
        String assignmentId,
        String executionAttemptId,
        String dispatchRequestId,
        String agentId,
        String agentSessionId,
        String ownerGatewayNodeId,
        String correlationId,
        OffsetDateTime requestedAt) implements ModuleEvent {

    public static final String TYPE = "a2a.runtime.cancel.requested.v1";

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateType() {
        return "A2A_CANCELLATION";
    }

    @Override
    public String aggregateId() {
        return cancellationId;
    }

    @Override
    public String rootTaskId() {
        return childTaskId;
    }

    /**
     * ModuleEvent uses occurredAt() as the canonical event timestamp contract.
     * This event's existing requestedAt component is that timestamp.
     */
    @Override
    public OffsetDateTime occurredAt() {
        return requestedAt;
    }
}
