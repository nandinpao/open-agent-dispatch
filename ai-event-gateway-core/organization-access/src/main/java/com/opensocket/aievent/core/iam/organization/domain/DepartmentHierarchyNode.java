package com.opensocket.aievent.core.iam.organization.domain;

import java.util.Optional;

public record DepartmentHierarchyNode(DepartmentId departmentId, Optional<DepartmentId> parentDepartmentId, int depth) {
    public DepartmentHierarchyNode {
        parentDepartmentId = parentDepartmentId == null ? Optional.empty() : parentDepartmentId;
        if (depth < 0) throw new IllegalArgumentException("depth must be non-negative");
    }
}
