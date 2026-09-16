package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

/** Stable cross-module event contract persisted through the transactional module outbox. */
public interface ModuleEvent {
    String eventId();
    String eventType();
    String aggregateType();
    String aggregateId();
    OffsetDateTime occurredAt();

    default String tenantId(){return null;}
    default String rootTaskId(){return null;}
    /** Task directly represented by this event; defaults to the root Task for legacy event types. */
    default String taskId(){return rootTaskId();}
    /** Business-journey correlation. New async events must propagate this from their cause. */
    default String correlationId(){return null;}
    /** Direct event/command/callback that caused this event. */
    default String causationId(){return null;}
    default String traceId(){return null;}
    default String spanId(){return null;}
    default String actorType(){return "SYSTEM";}
    default String actorId(){return "opendispatch";}
    default String payloadVersion(){return "1";}
    default ModuleEventEnvelope envelope(){return ModuleEventEnvelope.from(this);}
}
