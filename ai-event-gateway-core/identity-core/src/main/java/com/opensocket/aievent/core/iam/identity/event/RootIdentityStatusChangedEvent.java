package com.opensocket.aievent.core.iam.identity.event;

import com.opensocket.aievent.core.iam.identity.domain.RootIdentityStatus;
import java.time.Instant;

public record RootIdentityStatusChangedEvent(String eventId, String subjectId, RootIdentityStatus previousStatus,
                                              RootIdentityStatus newStatus, String reason, long version,
                                              String actorId, String correlationId, Instant occurredAt)
        implements IdentityDomainEvent {
    public String eventType() { return "ROOT_IDENTITY_STATUS_CHANGED"; }
}
