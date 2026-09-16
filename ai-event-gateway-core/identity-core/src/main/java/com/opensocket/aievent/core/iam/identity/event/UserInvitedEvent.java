package com.opensocket.aievent.core.iam.identity.event;

import java.time.Instant;

public record UserInvitedEvent(String eventId, String subjectId, String username, String normalizedEmail,
                               String actorId, String correlationId, Instant occurredAt)
        implements IdentityDomainEvent {
    public String eventType() { return "USER_INVITED"; }
}
