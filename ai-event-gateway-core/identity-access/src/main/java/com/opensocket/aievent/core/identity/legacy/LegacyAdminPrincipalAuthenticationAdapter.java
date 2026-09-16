package com.opensocket.aievent.core.identity.legacy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.identity.AdminPrincipal;

/**
 * One-way compatibility adapter from the legacy Spring Security principal to the stable
 * AuthenticationContext contract. It does not resolve new IAM roles or permissions.
 */
public final class LegacyAdminPrincipalAuthenticationAdapter {
    private static final Duration LEGACY_CONTEXT_TTL = Duration.ofMinutes(15);

    public AuthenticationContext adapt(AdminPrincipal principal, Instant now) {
        if (principal == null) throw new IllegalArgumentException("principal is required");
        Instant issuedAt = now == null ? Instant.now() : now;
        TenantRef tenant = principal.selectedTenantId() == null || principal.selectedTenantId().isBlank()
                ? TenantRef.instance()
                : TenantRef.tenant(principal.selectedTenantId());
        return new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, principal.userId()),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, principal.userId()),
                tenant,
                Optional.empty(),
                AuthenticationAssurance.passwordOnly(issuedAt),
                SecurityEpoch.ZERO,
                Optional.empty(),
                issuedAt,
                issuedAt.plus(LEGACY_CONTEXT_TTL));
    }
}
