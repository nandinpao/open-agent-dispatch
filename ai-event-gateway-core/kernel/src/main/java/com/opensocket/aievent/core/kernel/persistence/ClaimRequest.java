package com.opensocket.aievent.core.kernel.persistence;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Framework-neutral request used by repositories that atomically claim work. */
public record ClaimRequest(
        String workerId,
        OffsetDateTime now,
        OffsetDateTime claimUntil,
        int limit) {

    public ClaimRequest {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(claimUntil, "claimUntil");
        if (!claimUntil.isAfter(now)) {
            throw new IllegalArgumentException("claimUntil must be after now");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        workerId = workerId.trim();
    }

    public static ClaimRequest forLease(
            String workerId,
            OffsetDateTime now,
            Duration lease,
            int limit) {
        Duration effectiveLease = lease == null || lease.isZero() || lease.isNegative()
                ? Duration.ofSeconds(30)
                : lease;
        return new ClaimRequest(workerId, now, now.plus(effectiveLease), limit);
    }

    public ClaimOwnership ownership() {
        return new ClaimOwnership(workerId, claimUntil);
    }
}
