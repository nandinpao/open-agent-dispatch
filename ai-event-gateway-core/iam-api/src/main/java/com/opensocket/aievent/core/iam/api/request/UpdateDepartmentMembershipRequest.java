package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembershipType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateDepartmentMembershipRequest(
        @NotNull DepartmentMembershipType membershipType,
        boolean primary,
        Instant expiresAt,
        String replacementMembershipId,
        @Min(1) Long replacementExpectedVersion,
        @NotBlank @Size(max=500) String reason) { }
