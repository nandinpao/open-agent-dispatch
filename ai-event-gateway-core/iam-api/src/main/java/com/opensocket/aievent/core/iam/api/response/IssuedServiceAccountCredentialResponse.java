package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.token.application.result.IssuedServiceAccountCredentialResult;
import java.time.Instant;

/** Client secret is returned once and is never available from read projections. */
public record IssuedServiceAccountCredentialResponse(
        String credentialId,
        String serviceAccountId,
        String clientId,
        String clientSecret,
        String last4,
        Instant issuedAt,
        Instant expiresAt) {
    public static IssuedServiceAccountCredentialResponse from(IssuedServiceAccountCredentialResult r) {
        return new IssuedServiceAccountCredentialResponse(
                r.credentialId(),r.serviceAccountId(),r.clientId(),r.clientSecret(),r.last4(),r.issuedAt(),r.expiresAt());
    }
}
