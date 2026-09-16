package com.opensocket.aievent.core.iam.token.application.result;

import java.time.Instant;
import java.util.Set;

/** Short-lived OAuth2 machine access-token result. */
public record IssuedMachineAccessTokenResult(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Set<String> scopes,
        String audience,
        String jwtId,
        String keyId,
        Instant issuedAt,
        Instant expiresAt) {}
