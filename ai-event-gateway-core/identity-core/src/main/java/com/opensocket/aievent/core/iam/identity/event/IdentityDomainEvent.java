package com.opensocket.aievent.core.iam.identity.event;

import java.time.Instant;

public sealed interface IdentityDomainEvent permits RootIdentityCreatedEvent, RootIdentityStatusChangedEvent,
        UserCreatedEvent, UserInvitedEvent, UserStatusChangedEvent {
    String eventId();
    String eventType();
    String subjectId();
    String actorId();
    String correlationId();
    Instant occurredAt();
}
