package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeTenantMembershipStatusRequest(
        @NotNull MembershipStatus status,
        @NotBlank @Size(max = 500) String reason) { }
