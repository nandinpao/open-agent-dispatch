package com.opensocket.aievent.core.iam.organization.domain;

import java.util.ArrayList;
import java.util.List;

/** Ordered root-to-node path with the historical labels required by AS_RECORDED reporting. */
public record DepartmentPath(
        List<DepartmentId> departmentIds,
        List<String> departmentCodes,
        List<String> departmentNames
) {
    public DepartmentPath {
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
        departmentCodes = departmentCodes == null ? List.of() : List.copyOf(departmentCodes);
        departmentNames = departmentNames == null ? List.of() : List.copyOf(departmentNames);
        if (departmentIds.size() != departmentCodes.size() || departmentIds.size() != departmentNames.size()) {
            throw new IllegalArgumentException("department path lists must have equal size");
        }
    }

    public static DepartmentPath empty() {
        return new DepartmentPath(List.of(), List.of(), List.of());
    }

    public DepartmentPath append(Department department) {
        ArrayList<DepartmentId> ids = new ArrayList<>(departmentIds);
        ArrayList<String> codes = new ArrayList<>(departmentCodes);
        ArrayList<String> names = new ArrayList<>(departmentNames);
        ids.add(department.departmentId());
        codes.add(department.code());
        names.add(department.name());
        return new DepartmentPath(ids, codes, names);
    }
}
