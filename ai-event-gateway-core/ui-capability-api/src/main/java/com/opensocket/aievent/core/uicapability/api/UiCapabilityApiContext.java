package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.time.Instant;
import java.util.Objects;

public record UiCapabilityApiContext(AuthenticationContext authentication, String correlationId, Instant requestedAt) {
    public UiCapabilityApiContext {
        Objects.requireNonNull(authentication, "authentication");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        correlationId = correlationId.trim();
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
