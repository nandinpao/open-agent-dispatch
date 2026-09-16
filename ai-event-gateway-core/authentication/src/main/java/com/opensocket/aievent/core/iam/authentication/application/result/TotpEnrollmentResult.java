package com.opensocket.aievent.core.iam.authentication.application.result;

import java.util.List;

public record TotpEnrollmentResult(
        String methodId,
        String secret,
        String otpauthUri,
        List<String> recoveryCodes,
        long expectedVersion) {}
