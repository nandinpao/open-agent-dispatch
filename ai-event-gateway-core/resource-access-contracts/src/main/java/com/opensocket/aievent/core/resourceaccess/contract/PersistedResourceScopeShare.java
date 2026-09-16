package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Durable RS1 Resource Scope Share evidence. It deliberately contains no permission code. */
public record PersistedResourceScopeShare(
        String shareId,
        ResourceScopeShare share,
        ResourceScopeShareState state,
        String idempotencyKey,
        long version,
        Instant updatedAt,
        String revokedBy,
        Instant revokedAt) {
    public PersistedResourceScopeShare {
        if (shareId == null || shareId.isBlank()) throw new IllegalArgumentException("shareId is required");
        shareId = shareId.trim();
        Objects.requireNonNull(share, "share");
        state = state == null ? ResourceScopeShareState.ACTIVE : state;
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        idempotencyKey = idempotencyKey.trim();
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(updatedAt, "updatedAt");
        revokedBy = revokedBy == null ? "" : revokedBy.trim();
        if (state == ResourceScopeShareState.REVOKED && (revokedBy.isEmpty() || revokedAt == null)) {
            throw new IllegalArgumentException("revoked share requires revokedBy/revokedAt");
        }
    }
    public boolean effectiveAt(Instant at) { return state == ResourceScopeShareState.ACTIVE && share.effectiveAt(at); }
}
