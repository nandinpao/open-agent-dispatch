package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

public record MembershipResponse(
        String membershipId,
        String membershipType,
        String tenantId,
        String userId,
        String resourceId,
        String role,
        String status,
        boolean primary,
        Instant effectiveAt,
        Instant expiresAt,
        long version,
        String employeeId) {

    /** Backward-compatible constructor for Department and Group memberships. */
    public MembershipResponse(
            String membershipId,
            String membershipType,
            String tenantId,
            String userId,
            String resourceId,
            String role,
            String status,
            boolean primary,
            Instant effectiveAt,
            Instant expiresAt,
            long version) {
        this(membershipId, membershipType, tenantId, userId, resourceId, role, status, primary,
                effectiveAt, expiresAt, version, null);
    }
}
