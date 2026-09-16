package com.opensocket.aievent.core.iam.identity.event;

import java.time.Instant;

public record RootIdentityCreatedEvent(String eventId, String subjectId, String actorId,
                                       String correlationId, Instant occurredAt) implements IdentityDomainEvent {
    public String eventType() { return "ROOT_IDENTITY_CREATED"; }
}
