package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record GovernanceExplicitDenyView(
        String denyId, String principalType, String principalId, String permissionCode,
        String resourceType, String scopeType, String scopeRefId, String severity,
        String state, String denyReason, String createdBy, String approvedBy,
        Instant validFrom, Instant validTo, long version, Instant updatedAt) {}
