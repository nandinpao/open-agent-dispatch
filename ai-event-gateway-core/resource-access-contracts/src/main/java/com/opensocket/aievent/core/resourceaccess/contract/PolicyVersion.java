package com.opensocket.aievent.core.resourceaccess.contract;

/** Monotonic Resource Access policy identity used by decisions, descriptors and caches. */
public record PolicyVersion(long catalogVersion, long policyRevision, String contentHash) {
    public static final PolicyVersion ZERO = new PolicyVersion(0, 0, "");
    public PolicyVersion {
        if (catalogVersion < 0 || policyRevision < 0) throw new IllegalArgumentException("policy versions must be non-negative");
        contentHash = contentHash == null ? "" : contentHash.trim();
    }
}
