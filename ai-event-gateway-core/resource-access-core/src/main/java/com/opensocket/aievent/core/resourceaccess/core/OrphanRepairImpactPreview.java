package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record OrphanRepairImpactPreview(
        String repairId, String resourceType, String resourceId, String resourceKey,
        long expectedResourceVersion, String currentSecurityState,
        String proposedOwnerDepartmentId, String proposedOwnerGroupId, String proposedStewardUserId,
        long participantCount, long activeGrantCount, long activeDenyCount, long childResourceCount,
        boolean highRisk, String warning, Instant evaluatedAt) {}
