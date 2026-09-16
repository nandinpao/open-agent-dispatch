package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

/** Non-sensitive INSTANCE-scope projection of a user's Tenant memberships. */
public record PlatformTenantMembershipResponse(
        String userId,
        String tenantId,
        String tenantCode,
        String tenantName,
        String membershipStatus,
        String tenantStatus,
        Instant expiresAt,
        boolean defaultTenant,
        String membershipSource,
        long membershipVersion,
        Instant updatedAt) { }
