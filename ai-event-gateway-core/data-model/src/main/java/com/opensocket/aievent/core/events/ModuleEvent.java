package com.opensocket.aievent.core.events;

import java.time.OffsetDateTime;

/** Stable cross-module event contract persisted through the transactional outbox. */
public interface ModuleEvent {
    String eventId();
    String eventType();
    String aggregateType();
    String aggregateId();
    OffsetDateTime occurredAt();
}
