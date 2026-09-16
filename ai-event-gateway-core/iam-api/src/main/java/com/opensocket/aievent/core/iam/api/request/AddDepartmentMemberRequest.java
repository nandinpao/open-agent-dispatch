package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembershipType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Object-centric Department member command used by the Tenant workspace UI. */
public record AddDepartmentMemberRequest(
        String membershipId,
        @NotBlank String userId,
        @NotNull DepartmentMembershipType membershipType,
        boolean primary,
        Instant expiresAt) {
    public AddDepartmentMembershipRequest toMembershipRequest(String departmentId) {
        return new AddDepartmentMembershipRequest(membershipId, departmentId, membershipType, primary, expiresAt);
    }
}
