package com.opensocket.aievent.core.iam.token.application.service;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.MachineAccessBoundary;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachineCredentialRef;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipal;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipalType;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.iam.token.application.result.ValidatedTokenResult;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import com.opensocket.aievent.core.iam.token.domain.ServiceAccount;
import com.opensocket.aievent.core.iam.token.domain.ServiceAccountStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 8A bridge from the existing Service Account / opaque access-token model into the canonical
 * framework-free machine authentication contract. It does not change Event Intake routing or
 * introduce JWT/OAuth issuance; those are later phases.
 */
public final class ServiceAccountMachineAuthenticationFactory {
    private ServiceAccountMachineAuthenticationFactory() {}

    public static MachineAuthenticationContext fromValidatedToken(
            ServiceAccount serviceAccount,
            ValidatedTokenResult validatedToken,
            Instant authenticatedAt) {
        Objects.requireNonNull(serviceAccount, "serviceAccount");
        Objects.requireNonNull(validatedToken, "validatedToken");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");

        requireServiceAccountToken(serviceAccount, validatedToken);
        if (serviceAccount.status() != ServiceAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Service account must be ACTIVE to create a machine authentication context");
        }
        if (!authenticatedAt.isBefore(serviceAccount.nextReviewAt())) {
            throw new IllegalArgumentException("Service account ownership review is overdue");
        }
        if (!authenticatedAt.isBefore(validatedToken.expiresAt())) {
            throw new IllegalArgumentException("authenticatedAt must be before token expiration");
        }

        var effectiveScope = validatedToken.effectiveScope();
        MachinePrincipal principal = new MachinePrincipal(
                serviceAccount.serviceAccountId().value(),
                MachinePrincipalType.SERVICE_ACCOUNT,
                TenantRef.tenant(serviceAccount.tenantId())
        );
        MachineAccessBoundary boundary = new MachineAccessBoundary(
                effectiveScope.permissions(),
                serviceAccount.machineScopes(),
                effectiveScope.audiences(),
                serviceAccount.allowedSourceSystems(),
                effectiveScope.apiPrefixes(),
                effectiveScope.cidrs().stream().map(cidr -> cidr.notation()).collect(java.util.stream.Collectors.toSet()),
                Map.of()
        );
        AuthenticationAssurance assurance = new AuthenticationAssurance(
                AuthenticationAssurance.Level.SYSTEM,
                Set.of("SERVICE_ACCOUNT_ACCESS_TOKEN"),
                authenticatedAt
        );
        return new MachineAuthenticationContext(
                principal,
                MachineCredentialRef.accessToken(validatedToken.tokenId()),
                boundary,
                assurance,
                validatedToken.securityEpoch(),
                validatedToken.issuedAt(),
                validatedToken.expiresAt()
        );
    }

    /**
     * Compatibility shim for Phase 8A callers. External scope/source arguments may only narrow
     * the persisted Service Account boundary and can never add authority.
     */
    @Deprecated
    public static MachineAuthenticationContext fromValidatedToken(
            ServiceAccount serviceAccount,
            ValidatedTokenResult validatedToken,
            Collection<String> protocolScopes,
            Collection<String> sourceSystems,
            Instant authenticatedAt) {
        MachineAuthenticationContext context = fromValidatedToken(serviceAccount, validatedToken, authenticatedAt);
        Set<String> requestedScopes = protocolScopes == null ? Set.of() : Set.copyOf(protocolScopes);
        Set<String> requestedSources = sourceSystems == null ? Set.of() : Set.copyOf(sourceSystems);
        if (!serviceAccount.machineScopes().containsAll(requestedScopes)) {
            throw new IllegalArgumentException("Requested machine scopes exceed the persisted Service Account boundary");
        }
        if (!serviceAccount.allowedSourceSystems().containsAll(requestedSources)) {
            throw new IllegalArgumentException("Requested Source Systems exceed the persisted Service Account boundary");
        }
        return context;
    }

    /** Builds the canonical short-lived machine context after a client credential has been validated. */
    public static MachineAuthenticationContext fromValidatedCredential(
            ServiceAccount serviceAccount,
            com.opensocket.aievent.core.iam.token.application.result.ValidatedServiceAccountCredentialResult credential,
            java.util.Set<String> effectivePermissions,
            com.opensocket.aievent.core.iam.security.contract.SecurityEpoch securityEpoch,
            Instant authenticatedAt,
            Instant accessTokenExpiresAt) {
        Objects.requireNonNull(serviceAccount, "serviceAccount");
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt");
        if (!serviceAccount.tenantId().equals(credential.tenantId())) {
            throw new IllegalArgumentException("Service account/credential Tenant mismatch");
        }
        if (!serviceAccount.serviceAccountId().value().equals(credential.serviceAccountId())) {
            throw new IllegalArgumentException("Service account/credential principal mismatch");
        }
        if (serviceAccount.status() != ServiceAccountStatus.ACTIVE || !authenticatedAt.isBefore(serviceAccount.nextReviewAt())) {
            throw new IllegalArgumentException("Service account is not eligible for machine authentication");
        }
        Instant effectiveExpiry = accessTokenExpiresAt.isBefore(credential.expiresAt()) ? accessTokenExpiresAt : credential.expiresAt();
        if (!effectiveExpiry.isAfter(authenticatedAt)) throw new IllegalArgumentException("Machine access token would already be expired");

        var permitted = serviceAccount.restrictions().intersectPermissions(effectivePermissions == null ? Set.of() : effectivePermissions);
        MachinePrincipal principal = new MachinePrincipal(
                serviceAccount.serviceAccountId().value(),
                MachinePrincipalType.SERVICE_ACCOUNT,
                TenantRef.tenant(serviceAccount.tenantId()));
        MachineAccessBoundary boundary = new MachineAccessBoundary(
                permitted.permissions(),
                serviceAccount.machineScopes(),
                serviceAccount.restrictions().audiences(),
                serviceAccount.allowedSourceSystems(),
                serviceAccount.restrictions().apiPrefixes(),
                serviceAccount.restrictions().cidrs().stream().map(c -> c.notation()).collect(java.util.stream.Collectors.toSet()),
                Map.of());
        AuthenticationAssurance assurance = new AuthenticationAssurance(
                AuthenticationAssurance.Level.SYSTEM, Set.of("OAUTH2_CLIENT_SECRET"), authenticatedAt);
        return new MachineAuthenticationContext(
                principal,
                new MachineCredentialRef(credential.credentialId(), MachineCredentialRef.CredentialType.CLIENT_SECRET, "", "opendispatch", credential.clientId()),
                boundary, assurance, securityEpoch, authenticatedAt, effectiveExpiry);
    }

    private static void requireServiceAccountToken(ServiceAccount account, ValidatedTokenResult token) {
        if (token.type() != AccessTokenType.SERVICE_ACCOUNT_TOKEN) {
            throw new IllegalArgumentException("Validated token must be SERVICE_ACCOUNT_TOKEN");
        }
        if (token.principal() == null || token.principal().principalType() != PrincipalRef.PrincipalType.SERVICE_ACCOUNT) {
            throw new IllegalArgumentException("Validated token principal must be SERVICE_ACCOUNT");
        }
        if (!account.tenantId().equals(token.tenantId())) {
            throw new IllegalArgumentException("Service account/token Tenant mismatch");
        }
        if (!account.serviceAccountId().value().equals(token.principal().principalId())) {
            throw new IllegalArgumentException("Service account/token principal mismatch");
        }
    }
}
