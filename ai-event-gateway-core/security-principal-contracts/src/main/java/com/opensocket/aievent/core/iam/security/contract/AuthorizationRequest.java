package com.opensocket.aievent.core.iam.security.contract;

import java.util.Map;
import java.util.Objects;

/** Framework-free authorization request consumed through AuthorizationPort. */
public record AuthorizationRequest(
        PrincipalRef principal,
        TenantRef activeTenant,
        String permission,
        String resourceType,
        String resourceId,
        String requestedScopeType,
        String requestedScopeId,
        SecurityEpoch presentedEpoch,
        Map<String, String> requestContext
) {
    public AuthorizationRequest {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(activeTenant, "activeTenant");
        permission = requireText(permission, "permission");
        resourceType = normalize(resourceType);
        resourceId = normalize(resourceId);
        requestedScopeType = normalize(requestedScopeType);
        requestedScopeId = normalize(requestedScopeId);
        presentedEpoch = presentedEpoch == null ? SecurityEpoch.ZERO : presentedEpoch;
        requestContext = requestContext == null ? Map.of() : Map.copyOf(requestContext);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
