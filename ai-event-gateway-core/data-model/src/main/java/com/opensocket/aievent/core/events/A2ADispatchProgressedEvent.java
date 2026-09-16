package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

/** Durable projection from Dispatch Authority evidence into the A2A operational stage. */
public record A2ADispatchProgressedEvent(
        String eventId, String tenantId, String taskId, String dispatchRequestId, String stage,
        String blockerCode, String reason, String evidenceReference, OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "a2a.dispatch-progressed.v1";
    @Override public String eventType() { return TYPE; }
    @Override public String aggregateType() { return "DISPATCH_REQUEST"; }
    @Override public String aggregateId() { return dispatchRequestId; }
    @Override public String rootTaskId() { return taskId; }
}
