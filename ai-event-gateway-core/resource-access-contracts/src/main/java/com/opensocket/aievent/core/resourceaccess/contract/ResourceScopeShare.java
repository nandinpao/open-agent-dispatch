package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/**
 * RS0 contract for a business-resource scope share.
 *
 * <p>There is deliberately no permissionCode in this record: a share is scope evidence only.
 * Authorization still requires an independent IAM/RBAC permission whose binding scope is compatible
 * with this target. This prevents Scope Share from becoming a parallel ACL system.</p>
 */
public record ResourceScopeShare(
        ResourceRef resourceRef,
        ResourceScopeShareTarget target,
        String reason,
        String createdBy,
        Instant createdAt,
        Instant expiresAt) {

    public ResourceScopeShare {
        Objects.requireNonNull(resourceRef, "resourceRef");
        Objects.requireNonNull(target, "target");
        if (!resourceRef.tenantId().equals(target.tenantId())) {
            throw new IllegalArgumentException("resource and share target must use the same tenant");
        }
        reason = required(reason, "reason");
        createdBy = required(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public boolean effectiveAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return !at.isBefore(createdAt) && (expiresAt == null || at.isBefore(expiresAt));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
