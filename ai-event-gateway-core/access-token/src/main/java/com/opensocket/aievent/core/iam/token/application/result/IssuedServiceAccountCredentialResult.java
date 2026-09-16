package com.opensocket.aievent.core.iam.token.application.result;

import java.time.Instant;

public record IssuedServiceAccountCredentialResult(
        String credentialId,
        String serviceAccountId,
        String clientId,
        String clientSecret,
        String last4,
        Instant issuedAt,
        Instant expiresAt) {}
