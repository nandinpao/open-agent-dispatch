package com.opensocket.aievent.core.identity.dto;

import java.time.OffsetDateTime;

/** Browser-safe description of a server-side Admin session. Raw session identifiers are never exposed. */
public record AdminSessionDescriptor(
        String sessionReference,
        String username,
        String userId,
        String selectedTenantId,
        OffsetDateTime createdAt,
        OffsetDateTime lastAccessedAt,
        OffsetDateTime expiresAt,
        boolean current,
        boolean expired
) {}
