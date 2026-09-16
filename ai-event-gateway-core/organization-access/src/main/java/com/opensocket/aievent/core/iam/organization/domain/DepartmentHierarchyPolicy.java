package com.opensocket.aievent.core.iam.organization.domain;

import java.util.Objects;
import java.util.Optional;

public final class DepartmentHierarchyPolicy {
    public static final int DEFAULT_MAX_DEPTH = 10;
    private final int maxDepth;

    public DepartmentHierarchyPolicy(int maxDepth) {
        if (maxDepth < 1 || maxDepth > DEFAULT_MAX_DEPTH) throw new IllegalArgumentException("maxDepth must be between 1 and 10");
        this.maxDepth = maxDepth;
    }

    public void validateCreate(
            TenantId tenantId,
            Optional<Department> parent,
            DepartmentHierarchySnapshot hierarchy
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(hierarchy, "hierarchy");
        requireSameTenant(tenantId, hierarchy.tenantId());
        parent = parent == null ? Optional.empty() : parent;
        if (parent.isPresent()) {
            Department resolvedParent = parent.orElseThrow();
            requireSameTenant(tenantId, resolvedParent.tenantId());
            requireActiveParent(resolvedParent);
            int resultingDepth = hierarchy.depthOf(resolvedParent.departmentId()) + 1;
            if (resultingDepth > maxDepth) {
                throw new OrganizationDomainException(
                        OrganizationReasonCode.DEPARTMENT_MAX_DEPTH_EXCEEDED,
                        "Department create would exceed max depth " + maxDepth
                );
            }
        }
    }

    public void validateMove(Department moving, Optional<Department> newParent, DepartmentHierarchySnapshot hierarchy) {
        Objects.requireNonNull(moving, "moving"); Objects.requireNonNull(hierarchy, "hierarchy");
        requireSameTenant(moving.tenantId(), hierarchy.tenantId());
        newParent = newParent == null ? Optional.empty() : newParent;
        int parentDepth = -1;
        if (newParent.isPresent()) {
            Department parent = newParent.orElseThrow();
            requireSameTenant(moving.tenantId(), parent.tenantId());
            requireActiveParent(parent);
            if (parent.departmentId().equals(moving.departmentId()) || hierarchy.descendantsOf(moving.departmentId()).contains(parent.departmentId())) {
                throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_CYCLE_DETECTED, "New parent is inside the moved subtree");
            }
            parentDepth = hierarchy.depthOf(parent.departmentId());
        }
        int resultingMaxDepth = parentDepth + 1 + hierarchy.subtreeHeight(moving.departmentId());
        if (resultingMaxDepth > maxDepth) {
            throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_MAX_DEPTH_EXCEEDED, "Department move would exceed max depth " + maxDepth);
        }
    }

    public int maxDepth() { return maxDepth; }

    private static void requireSameTenant(TenantId left, TenantId right) {
        if (!left.equals(right)) throw new OrganizationDomainException(OrganizationReasonCode.CROSS_TENANT_REFERENCE, "Cross-tenant organization reference is forbidden");
    }

    private static void requireActiveParent(Department parent) {
        if (parent.status() != DepartmentStatus.ACTIVE) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.DEPARTMENT_PARENT_DISABLED,
                    "Parent department must be active"
            );
        }
    }
}
