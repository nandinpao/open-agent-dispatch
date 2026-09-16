package com.opensocket.aievent.core.iam.token.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ServiceAccount {
    private final String tenantId;
    private final ServiceAccountId id;
    private final String name;
    private final String description;
    private final String ownerUserId;
    private final String ownerDepartmentId;
    private final String responsibilityBindingId;
    private final TokenScope restrictions;
    private final Set<String> machineScopes;
    private final Set<String> allowedSourceSystems;
    private final Duration tokenMaxTtl;
    private final int maxActiveTokens;
    private final Duration credentialMaxTtl;
    private final int maxActiveCredentials;
    private final int rateLimitPerMinute;
    private final Instant lastReviewedAt;
    private final Instant nextReviewAt;
    private final ServiceAccountRiskLevel riskLevel;
    private final ServiceAccountStatus status;
    private final String statusReason;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;
    private final long version;

    private ServiceAccount(
            String tenantId, ServiceAccountId id, String name, String description,
            String ownerUserId, String ownerDepartmentId, String responsibilityBindingId, TokenScope restrictions,
            Collection<String> machineScopes, Collection<String> allowedSourceSystems,
            Duration tokenMaxTtl, int maxActiveTokens,
            Duration credentialMaxTtl, int maxActiveCredentials,
            int rateLimitPerMinute, Instant lastReviewedAt, Instant nextReviewAt,
            ServiceAccountRiskLevel riskLevel, ServiceAccountStatus status, String statusReason,
            Instant createdAt, Instant updatedAt, String createdBy, String updatedBy, long version) {
        this.tenantId = TokenText.required(tenantId, "tenantId", 128);
        this.id = Objects.requireNonNull(id);
        this.name = TokenText.required(name, "name", 160);
        this.description = TokenText.optional(description, 1000);
        this.ownerUserId = TokenText.required(ownerUserId, "ownerUserId", 128);
        this.ownerDepartmentId = TokenText.required(ownerDepartmentId, "ownerDepartmentId", 128);
        this.responsibilityBindingId = TokenText.optional(responsibilityBindingId, 128);
        this.restrictions = validateRestrictions(restrictions);
        this.machineScopes = clean(machineScopes, "machineScope", 160);
        this.allowedSourceSystems = clean(allowedSourceSystems, "sourceSystem", 160);
        this.tokenMaxTtl = ttl(tokenMaxTtl, Duration.ofDays(90), "Service account token TTL");
        if (maxActiveTokens < 1 || maxActiveTokens > 10) throw new IllegalArgumentException("maxActiveTokens must be 1..10");
        this.maxActiveTokens = maxActiveTokens;
        this.credentialMaxTtl = ttl(credentialMaxTtl, Duration.ofDays(365), "Service account credential TTL");
        if (maxActiveCredentials < 1 || maxActiveCredentials > 10) throw new IllegalArgumentException("maxActiveCredentials must be 1..10");
        this.maxActiveCredentials = maxActiveCredentials;
        if (rateLimitPerMinute < 1) throw new IllegalArgumentException("rateLimitPerMinute must be positive");
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.lastReviewedAt = lastReviewedAt;
        this.nextReviewAt = Objects.requireNonNull(nextReviewAt);
        this.riskLevel = Objects.requireNonNull(riskLevel);
        this.status = Objects.requireNonNull(status);
        this.statusReason = TokenText.optional(statusReason, 500);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.createdBy = TokenText.required(createdBy, "createdBy", 128);
        this.updatedBy = TokenText.required(updatedBy, "updatedBy", 128);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
    }

    /** Backward-compatible creation entry point used by the Phase 1A-5 token baseline. */
    public static ServiceAccount create(
            String tenantId, ServiceAccountId id, String name, String description,
            String ownerUserId, String ownerDepartmentId, TokenScope restrictions,
            Duration maxTtl, int maxActive, int rateLimit, Instant nextReview,
            String actor, Instant now) {
        return create(tenantId, id, name, description, ownerUserId, ownerDepartmentId, "", restrictions,
                Set.of(), Set.of(), maxTtl, maxActive, Duration.ofDays(180), 2,
                rateLimit, nextReview, actor, now);
    }

    /** Backward-compatible Phase 8B creation signature. */
    public static ServiceAccount create(
            String tenantId, ServiceAccountId id, String name, String description,
            String ownerUserId, String ownerDepartmentId, TokenScope restrictions,
            Collection<String> machineScopes, Collection<String> allowedSourceSystems,
            Duration tokenMaxTtl, int maxActiveTokens,
            Duration credentialMaxTtl, int maxActiveCredentials,
            int rateLimitPerMinute, Instant nextReview, String actor, Instant now) {
        return create(tenantId,id,name,description,ownerUserId,ownerDepartmentId,"",restrictions,
                machineScopes,allowedSourceSystems,tokenMaxTtl,maxActiveTokens,credentialMaxTtl,maxActiveCredentials,
                rateLimitPerMinute,nextReview,actor,now);
    }

    public static ServiceAccount create(
            String tenantId, ServiceAccountId id, String name, String description,
            String ownerUserId, String ownerDepartmentId, String responsibilityBindingId, TokenScope restrictions,
            Collection<String> machineScopes, Collection<String> allowedSourceSystems,
            Duration tokenMaxTtl, int maxActiveTokens,
            Duration credentialMaxTtl, int maxActiveCredentials,
            int rateLimitPerMinute, Instant nextReview, String actor, Instant now) {
        Objects.requireNonNull(now, "now");
        if (nextReview == null || !nextReview.isAfter(now)) throw new IllegalArgumentException("nextReviewAt must be in the future");
        return new ServiceAccount(
                tenantId, id, name, description, ownerUserId, ownerDepartmentId, responsibilityBindingId, restrictions,
                machineScopes, allowedSourceSystems, tokenMaxTtl, maxActiveTokens,
                credentialMaxTtl, maxActiveCredentials, rateLimitPerMinute,
                now, nextReview, ServiceAccountRiskLevel.LOW, ServiceAccountStatus.ACTIVE, "",
                now, now, actor, actor, 1);
    }

    /** Backward-compatible reconstitution entry point for pre-Phase-8B rows. */
    public static ServiceAccount reconstitute(
            String tenantId, ServiceAccountId id, String name, String description,
            String ownerUserId, String ownerDepartmentId, TokenScope restrictions,
            Duration maxTtl, int maxActive, int rateLimit, Instant lastReviewed,
            Instant nextReview, ServiceAccountRiskLevel risk, ServiceAccountStatus status,
            String reason, Instant createdAt, Instant updatedAt, String createdBy,
            String updatedBy, long version) {
        return reconstitute(tenantId, id, name, description, ownerUserId, ownerDepartmentId, "",
                restrictions, Set.of(), Set.of(), maxTtl, maxActive,
                Duration.ofDays(180), 2, rateLimit, lastReviewed, nextReview, risk, status,
                reason, createdAt, updatedAt, createdBy, updatedBy, version);
    }

    public static ServiceAccount reconstitute(
            String tenantId, ServiceAccountId id, String name, String description,
            String ownerUserId, String ownerDepartmentId, String responsibilityBindingId, TokenScope restrictions,
            Collection<String> machineScopes, Collection<String> allowedSourceSystems,
            Duration tokenMaxTtl, int maxActiveTokens,
            Duration credentialMaxTtl, int maxActiveCredentials,
            int rateLimitPerMinute, Instant lastReviewed, Instant nextReview,
            ServiceAccountRiskLevel risk, ServiceAccountStatus status, String reason,
            Instant createdAt, Instant updatedAt, String createdBy, String updatedBy, long version) {
        return new ServiceAccount(
                tenantId, id, name, description, ownerUserId, ownerDepartmentId, responsibilityBindingId, restrictions,
                machineScopes, allowedSourceSystems, tokenMaxTtl, maxActiveTokens,
                credentialMaxTtl, maxActiveCredentials, rateLimitPerMinute,
                lastReviewed, nextReview, risk, status, reason,
                createdAt, updatedAt, createdBy, updatedBy, version);
    }

    /** Backward-compatible Phase 8B machine-boundary update signature. */
    public ServiceAccount updateMachineBoundary(
            TokenScope nextRestrictions,
            Collection<String> nextMachineScopes,
            Collection<String> nextSourceSystems,
            Duration nextCredentialMaxTtl,
            int nextMaxActiveCredentials,
            String actor,
            Instant now) {
        return updateMachineBoundary(responsibilityBindingId,nextRestrictions,nextMachineScopes,nextSourceSystems,
                nextCredentialMaxTtl,nextMaxActiveCredentials,actor,now);
    }

    public ServiceAccount updateMachineBoundary(
            String nextResponsibilityBindingId,
            TokenScope nextRestrictions,
            Collection<String> nextMachineScopes,
            Collection<String> nextSourceSystems,
            Duration nextCredentialMaxTtl,
            int nextMaxActiveCredentials,
            String actor,
            Instant now) {
        requireMutable();
        return new ServiceAccount(
                tenantId, id, name, description, ownerUserId, ownerDepartmentId, nextResponsibilityBindingId,
                nextRestrictions, nextMachineScopes, nextSourceSystems,
                tokenMaxTtl, maxActiveTokens, nextCredentialMaxTtl, nextMaxActiveCredentials,
                rateLimitPerMinute, lastReviewedAt, nextReviewAt, riskLevel, status, statusReason,
                createdAt, Objects.requireNonNull(now), createdBy, actor, version + 1);
    }

    public ServiceAccount reviewOwnership(String ownerUser, String department, Instant next, String actor, Instant now) {
        if (next == null || now == null || !next.isAfter(now)) throw new IllegalArgumentException("nextReviewAt must be in the future");
        return new ServiceAccount(
                tenantId, id, name, description, ownerUser, department, responsibilityBindingId, restrictions,
                machineScopes, allowedSourceSystems, tokenMaxTtl, maxActiveTokens,
                credentialMaxTtl, maxActiveCredentials, rateLimitPerMinute,
                now, next, riskLevel, ServiceAccountStatus.ACTIVE, "",
                createdAt, now, createdBy, actor, version + 1);
    }

    public ServiceAccount requireOwnershipReview(String reason, String actor, Instant now) {
        return change(ServiceAccountStatus.OWNERSHIP_REVIEW, riskLevel, reason, actor, now);
    }

    public ServiceAccount suspendRisk(ServiceAccountRiskLevel risk, String reason, String actor, Instant now) {
        if (risk == null || risk == ServiceAccountRiskLevel.LOW) throw new IllegalArgumentException("risk suspension requires MEDIUM or higher risk");
        return change(ServiceAccountStatus.SUSPENDED_RISK, risk, reason, actor, now);
    }

    public ServiceAccount activate(String reason, String actor, Instant now) {
        return change(ServiceAccountStatus.ACTIVE, riskLevel, reason, actor, now);
    }

    private ServiceAccount change(ServiceAccountStatus target, ServiceAccountRiskLevel risk, String reason, String actor, Instant now) {
        requireMutable();
        return new ServiceAccount(
                tenantId, id, name, description, ownerUserId, ownerDepartmentId, responsibilityBindingId, restrictions,
                machineScopes, allowedSourceSystems, tokenMaxTtl, maxActiveTokens,
                credentialMaxTtl, maxActiveCredentials, rateLimitPerMinute,
                lastReviewedAt, nextReviewAt, risk, target, TokenText.required(reason, "reason", 500),
                createdAt, now, createdBy, actor, version + 1);
    }

    private void requireMutable() {
        if (status == ServiceAccountStatus.DELETED) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_INVALID_STATUS, "Deleted service account is terminal");
        }
    }

    public boolean canIssue(Instant at) {
        return status == ServiceAccountStatus.ACTIVE && at != null && at.isBefore(nextReviewAt);
    }

    private static TokenScope validateRestrictions(TokenScope restrictions) {
        Objects.requireNonNull(restrictions, "restrictions");
        if (restrictions.permissions().isEmpty()) throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_SCOPE_INSUFFICIENT, "Service account requires an explicit permission scope");
        if (restrictions.audiences().isEmpty()) throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_AUDIENCE_DENIED, "Service account requires an allowed audience");
        if (restrictions.apiPrefixes().isEmpty()) throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_API_PREFIX_DENIED, "Service account requires an allowed API prefix");
        if (restrictions.cidrs().isEmpty()) throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_CIDR_REQUIRED, "Service account requires allowed CIDR");
        if (restrictions.permissions().stream().anyMatch(x -> x.startsWith("instance."))) {
            throw new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_INSTANCE_PERMISSION_FORBIDDEN, "Service account restrictions cannot contain Instance permissions");
        }
        return restrictions;
    }

    private static Duration ttl(Duration value, Duration maximum, String label) {
        Objects.requireNonNull(value, "ttl");
        if (value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_TTL_EXCEEDED, label + " exceeds the allowed maximum");
        }
        return value;
    }

    private static Set<String> clean(Collection<String> values, String field, int max) {
        if (values == null || values.isEmpty()) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) out.add(TokenText.required(value, field, max));
        return Collections.unmodifiableSet(out);
    }

    public String tenantId(){return tenantId;}
    public ServiceAccountId serviceAccountId(){return id;}
    public String name(){return name;}
    public String description(){return description;}
    public String ownerUserId(){return ownerUserId;}
    public String ownerDepartmentId(){return ownerDepartmentId;}
    public String responsibilityBindingId(){return responsibilityBindingId;}
    public TokenScope restrictions(){return restrictions;}
    public Set<String> machineScopes(){return machineScopes;}
    public Set<String> allowedSourceSystems(){return allowedSourceSystems;}
    public Duration tokenMaxTtl(){return tokenMaxTtl;}
    public int maxActiveTokens(){return maxActiveTokens;}
    public Duration credentialMaxTtl(){return credentialMaxTtl;}
    public int maxActiveCredentials(){return maxActiveCredentials;}
    public int rateLimitPerMinute(){return rateLimitPerMinute;}
    public Instant lastReviewedAt(){return lastReviewedAt;}
    public Instant nextReviewAt(){return nextReviewAt;}
    public ServiceAccountRiskLevel riskLevel(){return riskLevel;}
    public ServiceAccountStatus status(){return status;}
    public String statusReason(){return statusReason;}
    public Instant createdAt(){return createdAt;}
    public Instant updatedAt(){return updatedAt;}
    public String createdBy(){return createdBy;}
    public String updatedBy(){return updatedBy;}
    public long version(){return version;}
}
