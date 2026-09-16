package com.opensocket.aievent.core.iam.organization.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DepartmentHierarchyPolicyTest {
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");
    private static final TenantId TENANT = new TenantId("tenant-a");

    @Test void movingUnderDescendantIsRejected() {
        Department root = department("root", Optional.empty());
        Department child = department("child", Optional.of(root.departmentId()));
        Department leaf = department("leaf", Optional.of(child.departmentId()));
        Map<DepartmentId, DepartmentHierarchyNode> nodes = Map.of(
                root.departmentId(), new DepartmentHierarchyNode(root.departmentId(), Optional.empty(), 0),
                child.departmentId(), new DepartmentHierarchyNode(child.departmentId(), Optional.of(root.departmentId()), 1),
                leaf.departmentId(), new DepartmentHierarchyNode(leaf.departmentId(), Optional.of(child.departmentId()), 2));
        DepartmentHierarchyPolicy policy = new DepartmentHierarchyPolicy(10);
        OrganizationDomainException error = assertThrows(OrganizationDomainException.class, () ->
                policy.validateMove(root, Optional.of(leaf), new DepartmentHierarchySnapshot(TENANT, nodes)));
        assertEquals(OrganizationReasonCode.DEPARTMENT_CYCLE_DETECTED, error.reasonCode());
    }


    @Test void createUnderMaxDepthParentIsRejected() {
        Department parent = department("parent", Optional.empty());
        DepartmentHierarchySnapshot hierarchy = new DepartmentHierarchySnapshot(
                TENANT,
                Map.of(parent.departmentId(), new DepartmentHierarchyNode(parent.departmentId(), Optional.empty(), 2))
        );
        DepartmentHierarchyPolicy policy = new DepartmentHierarchyPolicy(2);
        OrganizationDomainException error = assertThrows(OrganizationDomainException.class, () ->
                policy.validateCreate(TENANT, Optional.of(parent), hierarchy));
        assertEquals(OrganizationReasonCode.DEPARTMENT_MAX_DEPTH_EXCEEDED, error.reasonCode());
    }

    @Test void movingUnderDisabledParentIsRejected() {
        Department moving = department("moving", Optional.empty());
        Department disabledParent = department("disabled-parent", Optional.empty()).disable("admin", NOW.plusSeconds(1));
        Map<DepartmentId, DepartmentHierarchyNode> nodes = Map.of(
                moving.departmentId(), new DepartmentHierarchyNode(moving.departmentId(), Optional.empty(), 0),
                disabledParent.departmentId(), new DepartmentHierarchyNode(disabledParent.departmentId(), Optional.empty(), 0));
        DepartmentHierarchyPolicy policy = new DepartmentHierarchyPolicy(10);

        OrganizationDomainException error = assertThrows(OrganizationDomainException.class, () ->
                policy.validateMove(moving, Optional.of(disabledParent), new DepartmentHierarchySnapshot(TENANT, nodes)));

        assertEquals(OrganizationReasonCode.DEPARTMENT_PARENT_DISABLED, error.reasonCode());
    }

    @Test void maxDepthIncludesSubtreeHeight() {
        Department root = department("root", Optional.empty());
        Department child = department("child", Optional.of(root.departmentId()));
        Department newParent = department("parent", Optional.empty());
        Map<DepartmentId, DepartmentHierarchyNode> nodes = Map.of(
                root.departmentId(), new DepartmentHierarchyNode(root.departmentId(), Optional.empty(), 0),
                child.departmentId(), new DepartmentHierarchyNode(child.departmentId(), Optional.of(root.departmentId()), 1),
                newParent.departmentId(), new DepartmentHierarchyNode(newParent.departmentId(), Optional.empty(), 2));
        DepartmentHierarchyPolicy policy = new DepartmentHierarchyPolicy(2);
        OrganizationDomainException error = assertThrows(OrganizationDomainException.class, () ->
                policy.validateMove(root, Optional.of(newParent), new DepartmentHierarchySnapshot(TENANT, nodes)));
        assertEquals(OrganizationReasonCode.DEPARTMENT_MAX_DEPTH_EXCEEDED, error.reasonCode());
    }

    private static Department department(String id, Optional<DepartmentId> parent) {
        return Department.create(TENANT, new DepartmentId(id), id.toUpperCase(), id, parent, Optional.empty(), 0, "admin", NOW);
    }
}
