package com.opensocket.aievent.core.identity.dto;

import java.time.OffsetDateTime;

public record AdminSessionRevocationResponse(
        String status,
        String sessionReference,
        boolean currentSession,
        OffsetDateTime revokedAt
) {}
