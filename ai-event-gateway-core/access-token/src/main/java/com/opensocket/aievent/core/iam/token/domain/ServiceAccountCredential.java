package com.opensocket.aievent.core.iam.token.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Persisted Service Account credential metadata. Clear-text client secrets never enter this
 * aggregate and are returned only once by the credential issuance application boundary.
 */
public final class ServiceAccountCredential {
    private final String tenantId;
    private final ServiceAccountCredentialId credentialId;
    private final ServiceAccountId serviceAccountId;
    private final ServiceAccountCredentialType credentialType;
    private final String name;
    private final String clientId;
    private final String last4;
    private final TokenHash secretHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant lastUsedAt;
    private final String rotatedFromCredentialId;
    private final Instant rotationGraceExpiresAt;
    private final Instant revokedAt;
    private final String revokedBy;
    private final String revocationReason;
    private final ServiceAccountCredentialStatus status;
    private final long useCount;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;
    private final long version;

    private ServiceAccountCredential(
            String tenantId,
            ServiceAccountCredentialId credentialId,
            ServiceAccountId serviceAccountId,
            ServiceAccountCredentialType credentialType,
            String name,
            String clientId,
            String last4,
            TokenHash secretHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant lastUsedAt,
            String rotatedFromCredentialId,
            Instant rotationGraceExpiresAt,
            Instant revokedAt,
            String revokedBy,
            String revocationReason,
            ServiceAccountCredentialStatus status,
            long useCount,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy,
            long version) {
        this.tenantId = TokenText.required(tenantId, "tenantId", 128);
        this.credentialId = Objects.requireNonNull(credentialId, "credentialId");
        this.serviceAccountId = Objects.requireNonNull(serviceAccountId, "serviceAccountId");
        this.credentialType = Objects.requireNonNull(credentialType, "credentialType");
        this.name = TokenText.required(name, "name", 160);
        this.clientId = TokenText.required(clientId, "clientId", 96);
        this.last4 = TokenText.required(last4, "last4", 4);
        if (this.last4.length() != 4) throw new IllegalArgumentException("last4 must contain exactly four characters");
        this.secretHash = Objects.requireNonNull(secretHash, "secretHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
        this.lastUsedAt = lastUsedAt;
        this.rotatedFromCredentialId = TokenText.optional(rotatedFromCredentialId, 128);
        this.rotationGraceExpiresAt = rotationGraceExpiresAt;
        this.revokedAt = revokedAt;
        this.revokedBy = TokenText.optional(revokedBy, 128);
        this.revocationReason = TokenText.optional(revocationReason, 500);
        this.status = Objects.requireNonNull(status, "status");
        if (useCount < 0) throw new IllegalArgumentException("useCount must not be negative");
        this.useCount = useCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.createdBy = TokenText.required(createdBy, "createdBy", 128);
        this.updatedBy = TokenText.required(updatedBy, "updatedBy", 128);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
    }

    public static ServiceAccountCredential issue(
            String tenantId,
            ServiceAccountCredentialId credentialId,
            ServiceAccountId serviceAccountId,
            String name,
            String clientId,
            String last4,
            TokenHash secretHash,
            Instant now,
            Duration ttl,
            String rotatedFromCredentialId,
            String actor) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofDays(365)) > 0) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_TTL_EXCEEDED,
                    "Service Account credential TTL must be within 365 days");
        }
        return new ServiceAccountCredential(
                tenantId,
                credentialId,
                serviceAccountId,
                ServiceAccountCredentialType.CLIENT_SECRET,
                name,
                clientId,
                last4,
                secretHash,
                now,
                now.plus(ttl),
                null,
                rotatedFromCredentialId,
                null,
                null,
                "",
                "",
                ServiceAccountCredentialStatus.ACTIVE,
                0,
                now,
                now,
                actor,
                actor,
                1);
    }

    public static ServiceAccountCredential reconstitute(
            String tenantId,
            ServiceAccountCredentialId credentialId,
            ServiceAccountId serviceAccountId,
            ServiceAccountCredentialType credentialType,
            String name,
            String clientId,
            String last4,
            TokenHash secretHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant lastUsedAt,
            String rotatedFromCredentialId,
            Instant rotationGraceExpiresAt,
            Instant revokedAt,
            String revokedBy,
            String revocationReason,
            ServiceAccountCredentialStatus status,
            long useCount,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy,
            long version) {
        return new ServiceAccountCredential(
                tenantId, credentialId, serviceAccountId, credentialType, name, clientId, last4,
                secretHash, issuedAt, expiresAt, lastUsedAt, rotatedFromCredentialId,
                rotationGraceExpiresAt, revokedAt, revokedBy, revocationReason, status, useCount,
                createdAt, updatedAt, createdBy, updatedBy, version);
    }

    public void assertUsable(Instant at) {
        Objects.requireNonNull(at, "at");
        if (!at.isBefore(expiresAt)) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_EXPIRED,
                    "Service Account credential is expired");
        }
        if (status == ServiceAccountCredentialStatus.REVOKED) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_REVOKED,
                    "Service Account credential was revoked");
        }
        if (status == ServiceAccountCredentialStatus.ROTATING
                && (rotationGraceExpiresAt == null || !at.isBefore(rotationGraceExpiresAt))) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_REVOKED,
                    "Rotated Service Account credential is outside its overlap window");
        }
    }

    public ServiceAccountCredential beginRotation(Duration overlap, String actor, Instant now) {
        assertUsable(now);
        Objects.requireNonNull(overlap, "overlap");
        if (status != ServiceAccountCredentialStatus.ACTIVE || overlap.isNegative() || overlap.compareTo(Duration.ofHours(24)) > 0) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_ROTATION_CONFLICT,
                    "Credential rotation overlap must be between 0 and 24 hours");
        }
        Instant requestedGrace = now.plus(overlap);
        Instant graceExpiresAt = requestedGrace.isBefore(expiresAt) ? requestedGrace : expiresAt;
        return copy(
                lastUsedAt,
                graceExpiresAt,
                null,
                "",
                "",
                ServiceAccountCredentialStatus.ROTATING,
                useCount,
                now,
                actor,
                version + 1);
    }

    public ServiceAccountCredential revoke(String reason, String actor, Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == ServiceAccountCredentialStatus.REVOKED) {
            throw new TokenDomainException(
                    TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_REVOKED,
                    "Service Account credential is already revoked");
        }
        return copy(
                lastUsedAt,
                rotationGraceExpiresAt,
                now,
                actor,
                TokenText.required(reason, "reason", 500),
                ServiceAccountCredentialStatus.REVOKED,
                useCount,
                now,
                actor,
                version + 1);
    }

    public ServiceAccountCredential used(Instant now) {
        assertUsable(now);
        return copy(
                now,
                rotationGraceExpiresAt,
                revokedAt,
                revokedBy,
                revocationReason,
                status,
                useCount + 1,
                now,
                updatedBy,
                version + 1);
    }

    private ServiceAccountCredential copy(
            Instant nextLastUsedAt,
            Instant nextRotationGraceExpiresAt,
            Instant nextRevokedAt,
            String nextRevokedBy,
            String nextRevocationReason,
            ServiceAccountCredentialStatus nextStatus,
            long nextUseCount,
            Instant nextUpdatedAt,
            String nextUpdatedBy,
            long nextVersion) {
        return new ServiceAccountCredential(
                tenantId,
                credentialId,
                serviceAccountId,
                credentialType,
                name,
                clientId,
                last4,
                secretHash,
                issuedAt,
                expiresAt,
                nextLastUsedAt,
                rotatedFromCredentialId,
                nextRotationGraceExpiresAt,
                nextRevokedAt,
                nextRevokedBy,
                nextRevocationReason,
                nextStatus,
                nextUseCount,
                createdAt,
                nextUpdatedAt,
                createdBy,
                nextUpdatedBy,
                nextVersion);
    }

    public String tenantId() { return tenantId; }
    public ServiceAccountCredentialId credentialId() { return credentialId; }
    public ServiceAccountId serviceAccountId() { return serviceAccountId; }
    public ServiceAccountCredentialType credentialType() { return credentialType; }
    public String name() { return name; }
    public String clientId() { return clientId; }
    public String last4() { return last4; }
    public TokenHash secretHash() { return secretHash; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public String rotatedFromCredentialId() { return rotatedFromCredentialId; }
    public Instant rotationGraceExpiresAt() { return rotationGraceExpiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public String revokedBy() { return revokedBy; }
    public String revocationReason() { return revocationReason; }
    public ServiceAccountCredentialStatus status() { return status; }
    public long useCount() { return useCount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public long version() { return version; }
}
