package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

/** Secret-free credential metadata. */
public record ServiceAccountCredentialResponse(
        String tenantId,
        String credentialId,
        String serviceAccountId,
        String credentialType,
        String name,
        String clientId,
        String last4,
        String status,
        Instant issuedAt,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant rotationGraceExpiresAt,
        long useCount,
        long version) {}
