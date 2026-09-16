package com.opensocket.aievent.core.uiaccessrequest.application;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import java.time.Instant;
import java.util.Objects;

public record SubmitGovernedAccessRequestCommand(
        AuthenticationContext authentication,
        String uiActionId,
        String resourceId,
        long expectedResourceVersion,
        VisibilityLevel requestedVisibility,
        long durationHours,
        String businessPurpose,
        String correlationId,
        String idempotencyKey,
        Instant requestedAt) {
    public SubmitGovernedAccessRequestCommand {
        Objects.requireNonNull(authentication, "authentication");
        uiActionId = required(uiActionId, "uiActionId");
        resourceId = required(resourceId, "resourceId");
        if (expectedResourceVersion < 1) throw new IllegalArgumentException("expectedResourceVersion must be positive");
        Objects.requireNonNull(requestedVisibility, "requestedVisibility");
        if (durationHours < 1 || durationHours > 720) throw new IllegalArgumentException("durationHours must be between 1 and 720");
        businessPurpose = required(businessPurpose, "businessPurpose");
        if (businessPurpose.length() < 20) throw new IllegalArgumentException("businessPurpose must contain at least 20 characters");
        correlationId = required(correlationId, "correlationId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
