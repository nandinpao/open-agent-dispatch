package com.opensocket.aievent.core.iam.security.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical authenticated machine context. It combines identity, credential evidence, and
 * credential-level access bounds without embedding an effective RBAC/resource authorization result.
 */
public record MachineAuthenticationContext(
        MachinePrincipal principal,
        MachineCredentialRef credential,
        MachineAccessBoundary accessBoundary,
        AuthenticationAssurance assurance,
        SecurityEpoch securityEpoch,
        Instant issuedAt,
        Instant expiresAt
) {
    public MachineAuthenticationContext {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(accessBoundary, "accessBoundary");
        Objects.requireNonNull(assurance, "assurance");
        if (assurance.level() != AuthenticationAssurance.Level.SYSTEM) {
            throw new IllegalArgumentException("machine authentication assurance must be SYSTEM");
        }
        securityEpoch = securityEpoch == null ? SecurityEpoch.ZERO : securityEpoch;
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }

    /**
     * Compatibility adapter for services that still consume the generic AuthenticationContext.
     * Machine-specific kind and access bounds remain authoritative in this context and are not
     * flattened into roles or permissions.
     */
    public AuthenticationContext toAuthenticationContext() {
        return new AuthenticationContext(
                principal.subjectRef(),
                principal.authorizationPrincipalRef(),
                principal.activeTenant(),
                Optional.empty(),
                assurance,
                securityEpoch,
                Optional.empty(),
                issuedAt,
                expiresAt
        );
    }
}
