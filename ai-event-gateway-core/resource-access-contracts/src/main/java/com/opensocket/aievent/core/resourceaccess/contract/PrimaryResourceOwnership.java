package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Objects;

/**
 * RS0 canonical primary organizational ownership descriptor.
 *
 * <p>This contract does not replace domain ownership tables. It defines the interoperable shape
 * that domain-owned descriptors must project into Resource Access.</p>
 */
public record PrimaryResourceOwnership(
        String tenantId,
        PrimaryResourceOwnerType ownerType,
        String ownerId) {

    public PrimaryResourceOwnership {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(ownerType, "ownerType");
        ownerId = required(ownerId, "ownerId");
        if (ownerType == PrimaryResourceOwnerType.TENANT && !tenantId.equals(ownerId)) {
            throw new IllegalArgumentException("TENANT ownerId must equal tenantId");
        }
    }

    public static PrimaryResourceOwnership tenant(String tenantId) {
        return new PrimaryResourceOwnership(tenantId, PrimaryResourceOwnerType.TENANT, tenantId);
    }

    public static PrimaryResourceOwnership department(String tenantId, String departmentId) {
        return new PrimaryResourceOwnership(tenantId, PrimaryResourceOwnerType.DEPARTMENT, departmentId);
    }

    public static PrimaryResourceOwnership group(String tenantId, String groupId) {
        return new PrimaryResourceOwnership(tenantId, PrimaryResourceOwnerType.GROUP, groupId);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
