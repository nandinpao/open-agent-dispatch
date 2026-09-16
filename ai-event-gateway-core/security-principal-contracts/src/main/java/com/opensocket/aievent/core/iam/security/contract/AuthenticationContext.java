package com.opensocket.aievent.core.iam.security.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable output of Authentication; it deliberately contains no permissions or roles. */
public record AuthenticationContext(
        SubjectRef subject,
        PrincipalRef principal,
        TenantRef activeTenant,
        Optional<SessionRef> session,
        AuthenticationAssurance assurance,
        SecurityEpoch securityEpoch,
        Optional<ExternalIssuerRef> externalIssuer,
        Instant issuedAt,
        Instant expiresAt
) {
    public AuthenticationContext {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(activeTenant, "activeTenant");
        session = session == null ? Optional.empty() : session;
        Objects.requireNonNull(assurance, "assurance");
        securityEpoch = securityEpoch == null ? SecurityEpoch.ZERO : securityEpoch;
        externalIssuer = externalIssuer == null ? Optional.empty() : externalIssuer;
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
}
