package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Membership IDs are server-generated when omitted. Tenant membership admits access but grants no RBAC authority. */
public record CreateTenantMembershipRequest(
        String membershipId,
        @NotBlank String userId,
        @NotNull MembershipStatus initialStatus,
        String employeeId,
        Instant expiresAt,
        boolean defaultTenant,
        @NotNull TenantMembershipSource membershipSource,
        @NotBlank @Size(max = 500) String reason) {
    public CreateTenantMembershipRequest {
        membershipId = normalizeOptional(membershipId);
        userId = userId == null ? null : userId.trim();
    }
    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
