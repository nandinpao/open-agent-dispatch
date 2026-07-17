package com.opensocket.aievent.core.identity.dto;

import java.time.OffsetDateTime;

public record AdminSecurityAuditEvent(
        String eventId,
        OffsetDateTime occurredAt,
        String eventType,
        String outcome,
        String username,
        String userId,
        String tenantId,
        String sessionReference,
        String sourceAddress,
        String userAgent,
        String reason
) {}
