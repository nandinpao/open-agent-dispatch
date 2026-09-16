package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

/** Result of an administrator initiated password setup/reset delivery. */
public record CredentialSetupResponse(
        String userId,
        String purpose,
        String deliveryId,
        String deliveryMethod,
        String deliveryStatus,
        String deliveryReference,
        Instant expiresAt,
        String failureCode,
        String setupActionUrl) {
    public CredentialSetupResponse {
        failureCode = failureCode == null ? "" : failureCode;
        setupActionUrl = setupActionUrl == null ? "" : setupActionUrl;
    }
}
