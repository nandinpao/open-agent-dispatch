package com.opensocket.aievent.core.iam.security.contract;

import java.util.Objects;

/**
 * Stable machine identity reference. A machine principal identifies who the caller is and which
 * Tenant boundary it belongs to; it does not carry an effective RBAC decision.
 */
public record MachinePrincipal(
        String principalId,
        MachinePrincipalType principalType,
        TenantRef activeTenant
) {
    public MachinePrincipal {
        principalId = requireText(principalId, "principalId");
        Objects.requireNonNull(principalType, "principalType");
        Objects.requireNonNull(activeTenant, "activeTenant");
        if (principalType != MachinePrincipalType.SYSTEM_SERVICE
                && activeTenant.scope() != TenantRef.Scope.TENANT) {
            throw new IllegalArgumentException(principalType + " must be Tenant-bound");
        }
    }

    /** Canonical projection into the shared authorization principal contract. */
    public PrincipalRef authorizationPrincipalRef() {
        PrincipalRef.PrincipalType type = switch (principalType) {
            case SERVICE_ACCOUNT, INTEGRATION -> PrincipalRef.PrincipalType.SERVICE_ACCOUNT;
            case AGENT -> PrincipalRef.PrincipalType.AGENT;
            case A2A_AGENT -> PrincipalRef.PrincipalType.A2A_AGENT;
            case SYSTEM_SERVICE -> PrincipalRef.PrincipalType.SYSTEM_SERVICE;
        };
        return new PrincipalRef(type, principalId);
    }

    /** Canonical projection into the shared authenticated-subject contract. */
    public SubjectRef subjectRef() {
        SubjectRef.IdentityType type = switch (principalType) {
            case SERVICE_ACCOUNT, INTEGRATION -> SubjectRef.IdentityType.SERVICE_ACCOUNT;
            case AGENT -> SubjectRef.IdentityType.AGENT;
            case A2A_AGENT -> SubjectRef.IdentityType.A2A_AGENT;
            case SYSTEM_SERVICE -> SubjectRef.IdentityType.SYSTEM_SERVICE;
        };
        return new SubjectRef(type, principalId);
    }

    public boolean tenantBound() {
        return activeTenant.scope() == TenantRef.Scope.TENANT;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
