package com.opensocket.aievent.core.iam.organization.event;

import java.time.Instant;

public interface OrganizationDomainEvent {
    String eventId(); String eventType(); String tenantId(); String subjectId(); String actorId(); String correlationId(); Instant occurredAt();
}
