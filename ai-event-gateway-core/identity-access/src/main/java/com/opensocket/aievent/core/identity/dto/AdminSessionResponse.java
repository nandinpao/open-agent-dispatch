package com.opensocket.aievent.core.identity.dto;

import java.time.OffsetDateTime;
import java.util.Set;

public record AdminSessionResponse(
        String authenticationType,
        String userId,
        String username,
        String displayName,
        Set<String> roles,
        Set<String> permissions,
        Set<String> allowedTenantIds,
        String selectedTenantId,
        OffsetDateTime authenticatedAt,
        OffsetDateTime expiresAt
) {
}
