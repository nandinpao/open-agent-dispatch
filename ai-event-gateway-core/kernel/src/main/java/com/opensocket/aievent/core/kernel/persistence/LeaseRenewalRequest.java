package com.opensocket.aievent.core.kernel.persistence;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Renews a lease only when the previous ownership fence still matches. */
public record LeaseRenewalRequest(
        String resourceId,
        ClaimOwnership ownership,
        OffsetDateTime now,
        OffsetDateTime newClaimUntil) {
    public LeaseRenewalRequest {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId is required");
        }
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(newClaimUntil, "newClaimUntil");
        if (!newClaimUntil.isAfter(now)) {
            throw new IllegalArgumentException("newClaimUntil must be after now");
        }
        resourceId = resourceId.trim();
    }
}
