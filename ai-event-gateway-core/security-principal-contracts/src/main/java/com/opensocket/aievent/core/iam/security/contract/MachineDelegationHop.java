package com.opensocket.aievent.core.iam.security.contract;

import java.time.Instant;
import java.util.Objects;

/** One immutable hop in a machine on-behalf-of/delegation chain. */
public record MachineDelegationHop(
        MachinePrincipal fromPrincipal,
        MachinePrincipal toPrincipal,
        String purpose,
        String resourceType,
        String resourceId,
        Instant delegatedAt
) {
    public MachineDelegationHop {
        Objects.requireNonNull(fromPrincipal, "fromPrincipal");
        Objects.requireNonNull(toPrincipal, "toPrincipal");
        if (!fromPrincipal.activeTenant().equals(toPrincipal.activeTenant())) {
            throw new IllegalArgumentException("machine delegation cannot cross Tenant boundaries");
        }
        purpose = required(purpose, "purpose");
        resourceType = optional(resourceType);
        resourceId = optional(resourceId);
        Objects.requireNonNull(delegatedAt, "delegatedAt");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? "" : value.trim(); }
}
