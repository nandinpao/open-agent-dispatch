package com.opensocket.aievent.core.iam.token.application.result;

import java.time.Instant;

public record ValidatedServiceAccountCredentialResult(
        String credentialId,
        String serviceAccountId,
        String clientId,
        String tenantId,
        Instant issuedAt,
        Instant expiresAt) {}
