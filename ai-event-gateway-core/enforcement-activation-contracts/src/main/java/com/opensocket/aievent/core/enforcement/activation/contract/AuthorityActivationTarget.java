package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;

/** Cluster-wide desired Authority Revision. Runtime nodes converge to this immutable target. */
public record AuthorityActivationTarget(
        long targetRevision,
        String targetChecksum,
        long generation,
        Instant updatedAt) {

    public AuthorityActivationTarget {
        if (targetRevision < 0) throw new IllegalArgumentException("targetRevision must not be negative");
        targetChecksum = targetChecksum == null ? "" : targetChecksum.trim();
        if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt is required");
    }

    public static AuthorityActivationTarget bootstrap() {
        return new AuthorityActivationTarget(0, "BOOTSTRAP_LEGACY_ONLY", 0, Instant.EPOCH);
    }
}
