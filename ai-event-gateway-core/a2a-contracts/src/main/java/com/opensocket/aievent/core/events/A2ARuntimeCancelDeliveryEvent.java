package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

public record A2ARuntimeCancelDeliveryEvent(
        String eventId,
        String tenantId,
        String cancellationId,
        String a2aRequestId,
        String childTaskId,
        boolean accepted,
        int httpStatus,
        String deliveryStatus,
        String errorCode,
        String message,
        OffsetDateTime occurredAt) implements ModuleEvent {
    public static final String TYPE = "a2a.runtime.cancel.delivery.v1";
    @Override public String eventType(){return TYPE;}
    @Override public String aggregateType(){return "A2A_CANCELLATION";}
    @Override public String aggregateId(){return cancellationId;}
    @Override public String rootTaskId(){return childTaskId;}
}
