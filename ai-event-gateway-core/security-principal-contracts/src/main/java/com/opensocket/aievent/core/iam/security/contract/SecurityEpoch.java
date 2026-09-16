package com.opensocket.aievent.core.iam.security.contract;

/** Monotonic security versions used to reject stale sessions, tokens and authorization caches. */
public record SecurityEpoch(long globalEpoch, long tenantEpoch, long principalEpoch) {
    public static final SecurityEpoch ZERO = new SecurityEpoch(0, 0, 0);

    public SecurityEpoch {
        if (globalEpoch < 0 || tenantEpoch < 0 || principalEpoch < 0) {
            throw new IllegalArgumentException("security epochs must be non-negative");
        }
    }

    public boolean isAtLeast(SecurityEpoch other) {
        if (other == null) return true;
        return globalEpoch >= other.globalEpoch
                && tenantEpoch >= other.tenantEpoch
                && principalEpoch >= other.principalEpoch;
    }
}
