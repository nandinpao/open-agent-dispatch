package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.uicapability.contract.UiPageBootstrapRequest;
import java.time.Instant;
import java.util.Objects;

public record UiPageBootstrapCommand(
        AuthenticationContext authentication,
        UiPageBootstrapRequest request,
        String correlationId,
        Instant requestedAt) {
    public UiPageBootstrapCommand {
        Objects.requireNonNull(authentication, "authentication");
        Objects.requireNonNull(request, "request");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
