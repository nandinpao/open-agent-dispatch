package com.opensocket.aievent.core.uiaccessrequest.application;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.time.Instant;
import java.util.Objects;

public record ReviewGovernedAccessRequestCommand(
        AuthenticationContext authentication,
        String requestId,
        long expectedRequestVersion,
        long expectedGrantVersion,
        long expectedResourceVersion,
        String reason,
        String correlationId,
        String idempotencyKey,
        Instant requestedAt) {
    public ReviewGovernedAccessRequestCommand {
        Objects.requireNonNull(authentication, "authentication");
        requestId = required(requestId, "requestId");
        if (expectedRequestVersion < 1 || expectedGrantVersion < 1 || expectedResourceVersion < 1)
            throw new IllegalArgumentException("expected versions must be positive");
        reason = required(reason, "reason");
        correlationId = required(correlationId, "correlationId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
