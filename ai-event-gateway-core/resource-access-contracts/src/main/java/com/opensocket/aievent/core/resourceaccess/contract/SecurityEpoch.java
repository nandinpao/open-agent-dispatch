package com.opensocket.aievent.core.resourceaccess.contract;

/** Composite monotonic epoch. A cached allow is valid only inside the same-or-newer namespace. */
public record SecurityEpoch(
        long globalEpoch,
        long tenantEpoch,
        long principalEpoch,
        long resourceEpoch,
        long policyCatalogVersion,
        long departmentTreeRevision) {
    public static final SecurityEpoch ZERO = new SecurityEpoch(0, 0, 0, 0, 0, 0);
    public SecurityEpoch {
        if (globalEpoch < 0 || tenantEpoch < 0 || principalEpoch < 0 || resourceEpoch < 0
                || policyCatalogVersion < 0 || departmentTreeRevision < 0) {
            throw new IllegalArgumentException("security epochs and revisions must be non-negative");
        }
    }
    public boolean isAtLeast(SecurityEpoch other) {
        if (other == null) return true;
        return globalEpoch >= other.globalEpoch && tenantEpoch >= other.tenantEpoch
                && principalEpoch >= other.principalEpoch && resourceEpoch >= other.resourceEpoch
                && policyCatalogVersion >= other.policyCatalogVersion
                && departmentTreeRevision >= other.departmentTreeRevision;
    }
}
