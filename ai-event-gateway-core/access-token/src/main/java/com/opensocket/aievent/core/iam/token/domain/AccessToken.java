package com.opensocket.aievent.core.iam.token.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import java.time.Instant;
import java.util.Objects;

/** Immutable persisted access-token metadata. The clear-text secret is never part of this aggregate. */
public final class AccessToken {
    private final String tenantId;
    private final TokenId tokenId;
    private final AccessTokenType type;
    private final PrincipalRef principal;
    private final String name;
    private final String prefix;
    private final String last4;
    private final TokenHash secretHash;
    private final TokenScope scope;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant lastUsedAt;
    private final String rotatedFromTokenId;
    private final Instant rotationGraceExpiresAt;
    private final Instant revokedAt;
    private final String revokedBy;
    private final String revocationReason;
    private final Instant consumedAt;
    private final AccessTokenStatus status;
    private final SecurityEpoch securityEpoch;
    private final long useCount;
    private final long version;

    private AccessToken(
            String tenantId,
            TokenId tokenId,
            AccessTokenType type,
            PrincipalRef principal,
            String name,
            String prefix,
            String last4,
            TokenHash secretHash,
            TokenScope scope,
            Instant issuedAt,
            Instant expiresAt,
            Instant lastUsedAt,
            String rotatedFromTokenId,
            Instant rotationGraceExpiresAt,
            Instant revokedAt,
            String revokedBy,
            String revocationReason,
            Instant consumedAt,
            AccessTokenStatus status,
            SecurityEpoch securityEpoch,
            long useCount,
            long version) {
        this.tenantId = TokenText.required(tenantId, "tenantId", 128);
        this.tokenId = Objects.requireNonNull(tokenId, "tokenId");
        this.type = Objects.requireNonNull(type, "type");
        this.principal = Objects.requireNonNull(principal, "principal");
        this.name = TokenText.required(name, "name", 160);
        this.prefix = TokenText.required(prefix, "prefix", 96);
        this.last4 = TokenText.required(last4, "last4", 4);
        if (last4.length() != 4) throw new IllegalArgumentException("last4 must contain exactly four characters");
        this.secretHash = Objects.requireNonNull(secretHash, "secretHash");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
        this.lastUsedAt = lastUsedAt;
        this.rotatedFromTokenId = TokenText.optional(rotatedFromTokenId, 128);
        this.rotationGraceExpiresAt = rotationGraceExpiresAt;
        if (rotationGraceExpiresAt != null && rotationGraceExpiresAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException("rotation grace cannot exceed token expiration");
        }
        this.revokedAt = revokedAt;
        this.revokedBy = TokenText.optional(revokedBy, 128);
        this.revocationReason = TokenText.optional(revocationReason, 500);
        this.consumedAt = consumedAt;
        this.status = Objects.requireNonNull(status, "status");
        this.securityEpoch = Objects.requireNonNull(securityEpoch, "securityEpoch");
        if (useCount < 0 || version < 1) throw new IllegalArgumentException("invalid counters");
        this.useCount = useCount;
        this.version = version;
    }

    public static AccessToken issue(
            String tenantId, TokenId id, AccessTokenType type, PrincipalRef principal, String name,
            String prefix, String last4, TokenHash hash, TokenScope scope, Instant now,
            Instant expiresAt, SecurityEpoch epoch, String rotatedFrom) {
        return new AccessToken(tenantId, id, type, principal, name, prefix, last4, hash, scope,
                now, expiresAt, null, rotatedFrom, null, null, "", "", null,
                AccessTokenStatus.ACTIVE, epoch, 0, 1);
    }

    public static AccessToken reconstitute(
            String tenantId, TokenId id, AccessTokenType type, PrincipalRef principal, String name,
            String prefix, String last4, TokenHash hash, TokenScope scope, Instant issuedAt,
            Instant expiresAt, Instant lastUsedAt, String rotatedFrom, Instant rotationGraceExpiresAt,
            Instant revokedAt, String revokedBy, String revocationReason, Instant consumedAt,
            AccessTokenStatus status, SecurityEpoch epoch, long useCount, long version) {
        return new AccessToken(tenantId, id, type, principal, name, prefix, last4, hash, scope,
                issuedAt, expiresAt, lastUsedAt, rotatedFrom, rotationGraceExpiresAt, revokedAt,
                revokedBy, revocationReason, consumedAt, status, epoch, useCount, version);
    }

    public void assertUsable(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == AccessTokenStatus.REVOKED) {
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_REVOKED, "Token is revoked");
        }
        if (status == AccessTokenStatus.CONSUMED) {
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_ALREADY_CONSUMED, "Token is consumed");
        }
        if (status != AccessTokenStatus.ACTIVE && status != AccessTokenStatus.ROTATING) {
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_INVALID, "Token is not active");
        }
        if (!now.isBefore(expiresAt)
                || (status == AccessTokenStatus.ROTATING
                    && rotationGraceExpiresAt != null
                    && !now.isBefore(rotationGraceExpiresAt))) {
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_EXPIRED, "Token is expired");
        }
    }

    public AccessToken beginRotation(Instant graceExpiresAt) {
        assertUsable(graceExpiresAt.minusNanos(1));
        if (type.oneTime()) {
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_TYPE_MISMATCH,
                    "One-time tokens cannot be rotated");
        }
        return new AccessToken(tenantId, tokenId, type, principal, name, prefix, last4, secretHash,
                scope, issuedAt, expiresAt, lastUsedAt, rotatedFromTokenId, graceExpiresAt,
                revokedAt, revokedBy, revocationReason, consumedAt, AccessTokenStatus.ROTATING,
                securityEpoch, useCount, version + 1);
    }

    public AccessToken used(Instant now) {
        assertUsable(now);
        return new AccessToken(tenantId, tokenId, type, principal, name, prefix, last4, secretHash,
                scope, issuedAt, expiresAt, now, rotatedFromTokenId, rotationGraceExpiresAt,
                revokedAt, revokedBy, revocationReason, consumedAt, status, securityEpoch,
                useCount + 1, version + 1);
    }

    public AccessToken revoke(String actor, String reason, Instant now) {
        if (status == AccessTokenStatus.REVOKED) return this;
        return new AccessToken(tenantId, tokenId, type, principal, name, prefix, last4, secretHash,
                scope, issuedAt, expiresAt, lastUsedAt, rotatedFromTokenId, rotationGraceExpiresAt,
                now, actor, TokenText.required(reason, "reason", 500), consumedAt,
                AccessTokenStatus.REVOKED, securityEpoch, useCount, version + 1);
    }

    public AccessToken consume(Instant now) {
        assertUsable(now);
        if (!type.oneTime()) {
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_TYPE_MISMATCH,
                    "Token is not one-time");
        }
        return new AccessToken(tenantId, tokenId, type, principal, name, prefix, last4, secretHash,
                scope, issuedAt, expiresAt, now, rotatedFromTokenId, rotationGraceExpiresAt,
                revokedAt, revokedBy, revocationReason, now, AccessTokenStatus.CONSUMED,
                securityEpoch, useCount + 1, version + 1);
    }

    public String tenantId() { return tenantId; }
    public TokenId tokenId() { return tokenId; }
    public AccessTokenType type() { return type; }
    public PrincipalRef principal() { return principal; }
    public String name() { return name; }
    public String prefix() { return prefix; }
    public String last4() { return last4; }
    public TokenHash secretHash() { return secretHash; }
    public TokenScope scope() { return scope; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public String rotatedFromTokenId() { return rotatedFromTokenId; }
    public Instant rotationGraceExpiresAt() { return rotationGraceExpiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public String revokedBy() { return revokedBy; }
    public String revocationReason() { return revocationReason; }
    public Instant consumedAt() { return consumedAt; }
    public AccessTokenStatus status() { return status; }
    public SecurityEpoch securityEpoch() { return securityEpoch; }
    public long useCount() { return useCount; }
    public long version() { return version; }
}
