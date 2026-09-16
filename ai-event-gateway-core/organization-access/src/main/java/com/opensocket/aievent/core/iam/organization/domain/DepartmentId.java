package com.opensocket.aievent.core.iam.organization.domain;

public record DepartmentId(String value) {
    public DepartmentId { value = OrganizationText.required(value, "departmentId", 128); }
}
