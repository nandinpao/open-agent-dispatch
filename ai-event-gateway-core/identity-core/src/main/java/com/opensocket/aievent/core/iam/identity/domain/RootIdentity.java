package com.opensocket.aievent.core.iam.identity.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Instance bootstrap and break-glass identity. It is never a tenant user or RBAC role. */
public final class RootIdentity {
    private static final Map<RootIdentityStatus, Set<RootIdentityStatus>> ALLOWED = transitions();

    private final RootIdentityId rootIdentityId;
    private final RootIdentityStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String updatedBy;
    private final String reason;
    private final long version;

    private RootIdentity(RootIdentityId rootIdentityId, RootIdentityStatus status, Instant createdAt, Instant updatedAt,
                         String updatedBy, String reason, long version) {
        this.rootIdentityId = Objects.requireNonNull(rootIdentityId, "rootIdentityId");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        this.updatedBy = DomainText.required(updatedBy, "updatedBy", 128);
        this.reason = DomainText.optional(reason, 500);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
    }

    public static RootIdentity bootstrapPending(String actorId, Instant at) {
        return new RootIdentity(RootIdentityId.INSTANCE, RootIdentityStatus.BOOTSTRAP_PENDING, at, at, actorId, "", 1);
    }

    public static RootIdentity reconstitute(RootIdentityStatus status, Instant createdAt, Instant updatedAt,
                                            String updatedBy, String reason, long version) {
        return new RootIdentity(RootIdentityId.INSTANCE, status, createdAt, updatedAt, updatedBy, reason, version);
    }

    public RootIdentity changeStatus(RootIdentityStatus target, String actorId, String reason, Instant at) {
        Objects.requireNonNull(target, "target");
        if (target == status) return this;
        if (!ALLOWED.getOrDefault(status, Set.of()).contains(target)) {
            throw new IdentityDomainException(IdentityReasonCode.ROOT_STATUS_TRANSITION_FORBIDDEN,
                    "Root status transition " + status + " -> " + target + " is not allowed");
        }
        return new RootIdentity(rootIdentityId, target, createdAt, at, actorId,
                DomainText.required(reason, "reason", 500), version + 1);
    }

    public IdentityType identityType() { return IdentityType.INSTANCE_ROOT; }
    public RootIdentityId rootIdentityId() { return rootIdentityId; }
    public RootIdentityStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }
    public String reason() { return reason; }
    public long version() { return version; }

    private static Map<RootIdentityStatus, Set<RootIdentityStatus>> transitions() {
        EnumMap<RootIdentityStatus, Set<RootIdentityStatus>> result = new EnumMap<>(RootIdentityStatus.class);
        result.put(RootIdentityStatus.BOOTSTRAP_PENDING, EnumSet.of(RootIdentityStatus.ACTIVE, RootIdentityStatus.DISABLED));
        result.put(RootIdentityStatus.ACTIVE, EnumSet.of(RootIdentityStatus.LOCKED_AFTER_RECOVERY, RootIdentityStatus.DISABLED));
        result.put(RootIdentityStatus.LOCKED_AFTER_RECOVERY, EnumSet.of(RootIdentityStatus.ACTIVE, RootIdentityStatus.DISABLED));
        result.put(RootIdentityStatus.DISABLED, EnumSet.of(RootIdentityStatus.LOCKED_AFTER_RECOVERY));
        return Map.copyOf(result);
    }
}
