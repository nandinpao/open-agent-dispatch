package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateTenantMembershipRequest(
        String employeeId,
        Instant expiresAt,
        boolean defaultTenant,
        @NotBlank @Size(max = 500) String reason) { }
