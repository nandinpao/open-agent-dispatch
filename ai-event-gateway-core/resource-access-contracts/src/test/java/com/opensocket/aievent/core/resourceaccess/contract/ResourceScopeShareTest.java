package com.opensocket.aievent.core.resourceaccess.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ResourceScopeShareTest {
    private static final Instant NOW = Instant.parse("2026-08-12T02:00:00Z");

    @Test
    void scopeShareIsTenantBoundAndTimeBound() {
        ResourceRef resource = new ResourceRef("tenant-a", ResourceType.TASK, "task-1");
        ResourceScopeShare share = new ResourceScopeShare(
                resource,
                new ResourceScopeShareTarget("tenant-a", CanonicalResourceScope.GROUP, "security-noc"),
                "Cross-team investigation",
                "admin-1",
                NOW,
                NOW.plusSeconds(3600));
        assertTrue(share.effectiveAt(NOW.plusSeconds(10)));
        assertFalse(share.effectiveAt(NOW.plusSeconds(3600)));
    }

    @Test
    void shareCannotCrossTenantOrUseTenantAsTarget() {
        ResourceRef resource = new ResourceRef("tenant-a", ResourceType.TASK, "task-1");
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceScopeShareTarget("tenant-a", CanonicalResourceScope.TENANT, "tenant-a"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceScopeShare(
                        resource,
                        new ResourceScopeShareTarget("tenant-b", CanonicalResourceScope.GROUP, "noc"),
                        "invalid cross tenant",
                        "admin-1",
                        NOW,
                        null));
    }
}
