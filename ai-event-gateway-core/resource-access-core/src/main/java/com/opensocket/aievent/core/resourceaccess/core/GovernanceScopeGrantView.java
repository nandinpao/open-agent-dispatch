package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record GovernanceScopeGrantView(
        String grantId, String principalType, String principalId, String permissionCode,
        String resourceType, String scopeType, String scopeRefId, String visibilityLevel,
        String state, String grantSource, String createdBy, String approvedBy,
        Instant validFrom, Instant validTo, long version, Instant updatedAt) {}
