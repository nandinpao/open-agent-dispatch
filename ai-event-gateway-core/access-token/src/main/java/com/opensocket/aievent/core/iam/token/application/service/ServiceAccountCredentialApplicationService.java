package com.opensocket.aievent.core.iam.token.application.service;

import com.opensocket.aievent.core.iam.token.application.command.*;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCredentialCommandPort;
import com.opensocket.aievent.core.iam.token.application.port.out.*;
import com.opensocket.aievent.core.iam.token.application.result.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.token.domain.*;
import com.opensocket.aievent.core.iam.token.event.TokenDomainEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Service Account client credential lifecycle. Clear-text secrets cross this boundary only once. */
public final class ServiceAccountCredentialApplicationService implements ServiceAccountCredentialCommandPort {
    private final ServiceAccountCredentialRepository credentials;
    private final ServiceAccountRepository accounts;
    private final ServiceAccountCredentialSecretPort secrets;
    private final TokenPermissionAuthorityPort authority;
    private final TokenSecurityEpochPort epochs;
    private final TokenEventPublisher events;
    private final Clock clock;

    public ServiceAccountCredentialApplicationService(
            ServiceAccountCredentialRepository credentials,
            ServiceAccountRepository accounts,
            ServiceAccountCredentialSecretPort secrets,
            TokenPermissionAuthorityPort authority,
            TokenSecurityEpochPort epochs,
            TokenEventPublisher events,
            Clock clock) {
        this.credentials = credentials;
        this.accounts = accounts;
        this.secrets = secrets;
        this.authority = authority;
        this.epochs = epochs;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public IssuedServiceAccountCredentialResult issue(IssueServiceAccountCredentialCommand command) {
        Instant now = clock.instant();
        ServiceAccount account = requireUsableAccount(command.tenantId(), command.serviceAccountId(), now);
        Duration ttl = command.ttl() == null ? account.credentialMaxTtl() : command.ttl();
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(account.credentialMaxTtl()) > 0) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_TTL_EXCEEDED,
                    "Requested credential TTL exceeds Service Account policy");
        }
        if (credentials.countUsable(command.tenantId(), account.serviceAccountId()) >= account.maxActiveCredentials()) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_ACTIVE_LIMIT_EXCEEDED, "Active Service Account credential limit reached");
        }
        return issueNew(account, command.name(), ttl, "", command.actorId(), command.correlationId(), now);
    }

    @Override
    public IssuedServiceAccountCredentialResult rotate(RotateServiceAccountCredentialCommand command) {
        Instant now = clock.instant();
        ServiceAccount account = requireUsableAccount(command.tenantId(), command.serviceAccountId(), now);
        ServiceAccountCredential old = find(command.tenantId(), command.credentialId());
        requireOwner(account, old);
        ServiceAccountCredential rotating = old.beginRotation(
                command.overlap() == null ? Duration.ZERO : command.overlap(), command.actorId(), now);
        credentials.save(rotating, old.version());
        Duration originalLifetime = Duration.between(old.issuedAt(), old.expiresAt());
        Duration replacementTtl = originalLifetime.compareTo(account.credentialMaxTtl()) > 0
                ? account.credentialMaxTtl()
                : originalLifetime;
        IssuedServiceAccountCredentialResult issued = issueNew(
                account,
                old.name(),
                replacementTtl,
                old.credentialId().value(),
                command.actorId(),
                command.correlationId(),
                now);
        publish("SERVICE_ACCOUNT_CREDENTIAL_ROTATED", account, old.credentialId().value(), command.actorId(), command.correlationId(), now,
                Map.of("replacementCredentialId", issued.credentialId()));
        return issued;
    }

    @Override
    public void revoke(RevokeServiceAccountCredentialCommand command) {
        Instant now = clock.instant();
        ServiceAccount account = findAccount(command.tenantId(), command.serviceAccountId());
        ServiceAccountCredential old = find(command.tenantId(), command.credentialId());
        requireOwner(account, old);
        ServiceAccountCredential revoked = old.revoke(command.reason(), command.actorId(), now);
        credentials.save(revoked, old.version());
        // Explicit credential revocation is a security event: invalidate all short-lived JWTs for
        // this Service Account through the existing principal security epoch. Rotation does not
        // bump the epoch, so normal overlap does not disrupt already-issued short-lived tokens.
        epochs.incrementPrincipal(command.tenantId(), new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT, account.serviceAccountId().value()), command.actorId());
        publish("SERVICE_ACCOUNT_CREDENTIAL_REVOKED", account, old.credentialId().value(), command.actorId(), command.correlationId(), now, Map.of("securityEpochInvalidated", "true"));
    }

    @Override
    public ValidatedServiceAccountCredentialResult validate(ValidateServiceAccountCredentialCommand command) {
        Instant now = clock.instant();
        ServiceAccountCredential credential = credentials.findByClientId(command.tenantId(), command.clientId())
                .orElseThrow(() -> new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_INVALID, "Invalid Service Account credential"));
        credential.assertUsable(now);
        ServiceAccount account = requireUsableAccount(command.tenantId(), credential.serviceAccountId().value(), now);
        if (!secrets.matches(command.clientSecret(), credential.secretHash())) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_INVALID, "Invalid Service Account credential");
        }
        if (!credentials.recordUsage(command.tenantId(), credential.credentialId(), now)) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_INVALID,
                    "Service Account credential became unusable during validation");
        }
        publish("SERVICE_ACCOUNT_CREDENTIAL_USED", account, credential.credentialId().value(), account.serviceAccountId().value(), command.correlationId(), now, Map.of());
        return new ValidatedServiceAccountCredentialResult(
                credential.credentialId().value(), account.serviceAccountId().value(), credential.clientId(),
                account.tenantId(), credential.issuedAt(), credential.expiresAt());
    }

    private IssuedServiceAccountCredentialResult issueNew(
            ServiceAccount account, String name, Duration ttl, String rotatedFrom,
            String actor, String correlationId, Instant now) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_TTL_EXCEEDED, "Credential TTL must be positive");
        }
        ServiceAccountCredentialSecretPort.GeneratedCredential generated = secrets.generate();
        ServiceAccountCredential credential = ServiceAccountCredential.issue(
                account.tenantId(), new ServiceAccountCredentialId(generated.credentialId()), account.serviceAccountId(),
                name, generated.clientId(), generated.last4(), generated.hash(), now, ttl, rotatedFrom, actor);
        credentials.save(credential, 0);
        publish("SERVICE_ACCOUNT_CREDENTIAL_CREATED", account, credential.credentialId().value(), actor, correlationId, now,
                rotatedFrom == null || rotatedFrom.isBlank() ? Map.of() : Map.of("rotatedFromCredentialId", rotatedFrom));
        return new IssuedServiceAccountCredentialResult(
                credential.credentialId().value(), account.serviceAccountId().value(), credential.clientId(),
                generated.clientSecret(), credential.last4(), credential.issuedAt(), credential.expiresAt());
    }

    private ServiceAccount requireUsableAccount(String tenantId, String serviceAccountId, Instant now) {
        ServiceAccount account = findAccount(tenantId, serviceAccountId);
        if (account.status() == ServiceAccountStatus.SUSPENDED_RISK) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_RISK_SUSPENDED, "Service account is risk suspended");
        }
        if (account.status() == ServiceAccountStatus.OWNERSHIP_REVIEW || !now.isBefore(account.nextReviewAt())) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_OWNERSHIP_REVIEW_REQUIRED, "Service account ownership review is required");
        }
        if (account.status() != ServiceAccountStatus.ACTIVE) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_INVALID_STATUS, "Service account is not active");
        }
        authority.requireActivePrincipal(tenantId, new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT, serviceAccountId));
        return account;
    }

    private ServiceAccount findAccount(String tenantId, String serviceAccountId) {
        return accounts.find(tenantId, new ServiceAccountId(serviceAccountId))
                .orElseThrow(() -> new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_NOT_FOUND, "Service account not found"));
    }

    private ServiceAccountCredential find(String tenantId, String credentialId) {
        return credentials.find(tenantId, new ServiceAccountCredentialId(credentialId))
                .orElseThrow(() -> new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_NOT_FOUND, "Service Account credential not found"));
    }

    private static void requireOwner(ServiceAccount account, ServiceAccountCredential credential) {
        if (!account.tenantId().equals(credential.tenantId()) || !account.serviceAccountId().equals(credential.serviceAccountId())) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_NOT_FOUND, "Service Account credential not found");
        }
    }

    private void publish(String eventType, ServiceAccount account, String credentialId, String actor, String correlationId, Instant at, Map<String,String> metadata) {
        events.publish(new TokenDomainEvent(
                UUID.randomUUID().toString(), eventType, account.tenantId(), "SERVICE_ACCOUNT",
                account.serviceAccountId().value(), credentialId, actor, correlationId, "", at, metadata));
    }

}
