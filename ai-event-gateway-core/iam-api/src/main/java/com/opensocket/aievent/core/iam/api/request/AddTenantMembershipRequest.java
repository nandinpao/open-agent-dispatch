package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/** Backward-compatible tenant membership command with optional server-generated membershipId. */
public record AddTenantMembershipRequest(
        String membershipId,
        @NotBlank String tenantId,
        String employeeId,
        Instant expiresAt) {
}
