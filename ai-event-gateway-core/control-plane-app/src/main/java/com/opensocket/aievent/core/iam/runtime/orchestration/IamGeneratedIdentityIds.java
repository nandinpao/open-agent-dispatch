package com.opensocket.aievent.core.iam.runtime.orchestration;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Generates retry-stable identity IDs within an explicit authority namespace. */
final class IamGeneratedIdentityIds {
    private IamGeneratedIdentityIds() { }

    static String resolve(String suppliedUserId, String authorityNamespace, String idempotencyKey) {
        if (suppliedUserId != null && !suppliedUserId.isBlank()) {
            return suppliedUserId.trim();
        }
        if (authorityNamespace == null || authorityNamespace.isBlank()) {
            throw new IllegalArgumentException("IAM_IDENTITY_AUTHORITY_NAMESPACE_REQUIRED");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("IAM_IDEMPOTENCY_KEY_REQUIRED_FOR_GENERATED_USER_ID");
        }
        String seed = "OPENDISPATCH_IAM_USER:"
                + authorityNamespace.trim()
                + ":"
                + idempotencyKey.trim();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
