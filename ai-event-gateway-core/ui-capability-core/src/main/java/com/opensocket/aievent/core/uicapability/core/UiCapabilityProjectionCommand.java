package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.uicapability.contract.UiCapabilityContextRequest;
import java.time.Instant;
import java.util.Objects;

/** Trusted authentication and correlation are server supplied; only context payload originates from the browser. */
public record UiCapabilityProjectionCommand(
        String contractVersion,
        AuthenticationContext authentication,
        UiCapabilityContextRequest context,
        String correlationId,
        Instant requestedAt) {
    public UiCapabilityProjectionCommand {
        contractVersion = require(contractVersion, "contractVersion");
        Objects.requireNonNull(authentication, "authentication");
        Objects.requireNonNull(context, "context");
        correlationId = require(correlationId, "correlationId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
