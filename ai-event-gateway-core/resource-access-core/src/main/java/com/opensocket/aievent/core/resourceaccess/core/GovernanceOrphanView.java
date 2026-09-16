package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record GovernanceOrphanView(
        String repairId, String resourceType, String resourceId, String resourceKey,
        String repairStatus, String reasonCode, String assignedTo,
        String proposedOwnerDepartmentId, String proposedOwnerGroupId, String proposedStewardUserId,
        long resourceVersion, long version, Instant detectedAt, Instant updatedAt) {}
