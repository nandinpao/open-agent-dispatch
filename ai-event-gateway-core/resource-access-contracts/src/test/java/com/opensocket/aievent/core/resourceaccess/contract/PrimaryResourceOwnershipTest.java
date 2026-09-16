package com.opensocket.aievent.core.resourceaccess.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PrimaryResourceOwnershipTest {
    @Test
    void tenantOwnerMustUseSameTenantId() {
        assertEquals("tenant-a", PrimaryResourceOwnership.tenant("tenant-a").ownerId());
        assertThrows(IllegalArgumentException.class,
                () -> new PrimaryResourceOwnership("tenant-a", PrimaryResourceOwnerType.TENANT, "tenant-b"));
    }

    @Test
    void departmentAndGroupOwnersAreTenantBound() {
        assertEquals(PrimaryResourceOwnerType.DEPARTMENT,
                PrimaryResourceOwnership.department("tenant-a", "finance").ownerType());
        assertEquals(PrimaryResourceOwnerType.GROUP,
                PrimaryResourceOwnership.group("tenant-a", "noc").ownerType());
    }
}
