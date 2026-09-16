package com.opensocket.aievent.core.iam.identity.event;

import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import java.time.Instant;

public record UserCreatedEvent(String eventId, String subjectId, String username, AccountStatus status,
                               UserCreationMode creationMode, String actorId, String correlationId, Instant occurredAt)
        implements IdentityDomainEvent {
    public String eventType() { return "USER_CREATED"; }
}
