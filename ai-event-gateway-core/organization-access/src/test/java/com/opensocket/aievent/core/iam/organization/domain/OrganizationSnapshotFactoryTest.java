package com.opensocket.aievent.core.iam.organization.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrganizationSnapshotFactoryTest {
    @Test void contentHashIsDeterministicAndGroupOrderIndependent() {
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        DepartmentRevision revision = new DepartmentRevision(new TenantId("tenant-a"), new DepartmentId("erp"), 3,
                "ERP", "ERP Department", Optional.empty(), List.of(new DepartmentId("it"), new DepartmentId("erp")),
                List.of("IT", "ERP"), List.of("Information Technology", "ERP Department"), now, Optional.empty(),
                DepartmentRevisionChangeType.MOVED, "admin", "Reorganization", now);
        OrganizationSnapshotFactory factory = new OrganizationSnapshotFactory();
        OrganizationSnapshot first = factory.capture(new SnapshotId("s-1"), revision, Set.of(new GroupId("g-2"), new GroupId("g-1")), now);
        OrganizationSnapshot second = factory.capture(new SnapshotId("s-2"), revision, Set.of(new GroupId("g-1"), new GroupId("g-2")), now.plusSeconds(10));
        assertEquals(first.contentHash(), second.contentHash());
        assertEquals(64, first.contentHash().length());
    }
}
