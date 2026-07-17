package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

public record DispatchDeadLetteredEvent(
        String eventId,
        String dispatchRequestId,
        String assignmentId,
        String taskId,
        String incidentId,
        String agentId,
        int attemptCount,
        String reason,
        OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "dispatch.dead-lettered.v1";
    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "DISPATCH_REQUEST"; }
    @Override public String aggregateId() { return dispatchRequestId; }
}
