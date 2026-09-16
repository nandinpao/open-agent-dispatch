package com.opensocket.aievent.core.iam.identity.event;

import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import java.time.Instant;

public record UserStatusChangedEvent(String eventId, String subjectId, AccountStatus previousStatus,
                                     AccountStatus newStatus, String reason, long version, String actorId,
                                     String correlationId, Instant occurredAt) implements IdentityDomainEvent {
    public String eventType() { return "USER_STATUS_CHANGED"; }
}
