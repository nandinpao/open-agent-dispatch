package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record AccessReviewItem(
        String itemId, String campaignId, AccessReviewSourceType sourceType, String sourceId,
        String principalType, String principalId, String permissionCode,
        String resourceType, String scopeType, String scopeRefId,
        String riskLevel, AccessReviewItemStatus status, String reviewerId,
        String decisionReason, boolean actionRequired, long version,
        Instant dueAt, Instant reviewedAt, Instant createdAt, Instant updatedAt) {}
