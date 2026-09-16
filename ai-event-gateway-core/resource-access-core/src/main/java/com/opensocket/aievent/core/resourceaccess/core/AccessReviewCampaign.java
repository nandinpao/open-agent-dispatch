package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record AccessReviewCampaign(
        String campaignId, String campaignName, String description,
        String resourceTypeFilter, String principalTypeFilter,
        AccessReviewCampaignStatus status, String createdBy, String activatedBy,
        String completedBy, Instant dueAt, long totalItems, long openItems,
        long version, Instant createdAt, Instant updatedAt) {}
