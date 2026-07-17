package com.opensocket.aievent.core.kernel.persistence;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Ownership fence for a claimed row. Both worker and lease boundary must still match. */
public record ClaimOwnership(String workerId, OffsetDateTime claimUntil) {
    public ClaimOwnership {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        Objects.requireNonNull(claimUntil, "claimUntil");
        workerId = workerId.trim();
    }
}
