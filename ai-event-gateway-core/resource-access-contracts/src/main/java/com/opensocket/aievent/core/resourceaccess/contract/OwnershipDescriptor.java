package com.opensocket.aievent.core.resourceaccess.contract;

/** Server-resolved ownership projection. Empty values mean not applicable, never caller supplied. */
public record OwnershipDescriptor(
        String ownerDepartmentId,
        String ownerGroupId,
        String stewardUserId,
        String custodianServiceId,
        String requesterDepartmentId,
        String executorDepartmentId,
        long ownershipVersion) {
    public OwnershipDescriptor {
        ownerDepartmentId = normalize(ownerDepartmentId);
        ownerGroupId = normalize(ownerGroupId);
        stewardUserId = normalize(stewardUserId);
        custodianServiceId = normalize(custodianServiceId);
        requesterDepartmentId = normalize(requesterDepartmentId);
        executorDepartmentId = normalize(executorDepartmentId);
        if (ownershipVersion < 0) throw new IllegalArgumentException("ownershipVersion must be non-negative");
    }
    public static OwnershipDescriptor unowned(long version) { return new OwnershipDescriptor("", "", "", "", "", "", version); }
    public boolean hasOwner() { return !ownerDepartmentId.isEmpty() || !ownerGroupId.isEmpty() || !stewardUserId.isEmpty(); }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
