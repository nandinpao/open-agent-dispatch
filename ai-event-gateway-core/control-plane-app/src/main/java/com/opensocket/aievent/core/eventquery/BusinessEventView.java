package com.opensocket.aievent.core.eventquery;

import java.time.OffsetDateTime;

/** Metadata-only RS2 Business Event view. Sensitive body content is retrieved separately. */
public record BusinessEventView(
        String eventId,
        String tenantId,
        String sourceSystem,
        String eventType,
        String eventStage,
        String correlationId,
        String normalizedMessage,
        String decisionType,
        boolean duplicate,
        long occurrenceCount,
        String incidentId,
        OffsetDateTime occurredAt,
        OffsetDateTime decidedAt,
        String ownerDepartmentId,
        String ownerGroupId,
        String scopeStatus,
        Long scopeSourceVersion,
        OffsetDateTime scopeInheritedAt) { }
