package com.opensocket.aievent.core.iam.security.contract;

import java.util.Objects;

/** Stable authorization principal reference; does not expose a User or Role entity. */
public record PrincipalRef(PrincipalType principalType, String principalId) {
    public PrincipalRef {
        Objects.requireNonNull(principalType, "principalType");
        if (principalId == null || principalId.isBlank()) throw new IllegalArgumentException("principalId is required");
        principalId = principalId.trim();
    }

    public enum PrincipalType {
        USER,
        SERVICE_ACCOUNT,
        AGENT,
        A2A_AGENT,
        DEPARTMENT,
        GROUP,
        INSTANCE_ROOT,
        SYSTEM_SERVICE
    }
}
