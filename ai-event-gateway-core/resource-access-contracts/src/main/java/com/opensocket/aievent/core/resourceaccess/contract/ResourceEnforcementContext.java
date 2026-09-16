package com.opensocket.aievent.core.resourceaccess.contract;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.time.Instant;
import java.util.Objects;

/** Trusted runtime identity used by REST, internal-port and streaming enforcement adapters. */
public record ResourceEnforcementContext(
        AuthenticationContext authentication,
        String correlationId,
        Instant requestedAt) {
    public ResourceEnforcementContext {
        Objects.requireNonNull(authentication, "authentication");
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        correlationId = correlationId.trim();
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
