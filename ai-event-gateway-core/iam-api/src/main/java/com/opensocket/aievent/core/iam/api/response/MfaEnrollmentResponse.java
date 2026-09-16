package com.opensocket.aievent.core.iam.api.response;

import java.util.List;

public record MfaEnrollmentResponse(
        String methodId,
        String provisioningUri,
        String secretDisplay,
        List<String> recoveryCodes,
        long expectedVersion) {
    public MfaEnrollmentResponse {
        recoveryCodes = recoveryCodes == null ? List.of() : List.copyOf(recoveryCodes);
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
    }
}
