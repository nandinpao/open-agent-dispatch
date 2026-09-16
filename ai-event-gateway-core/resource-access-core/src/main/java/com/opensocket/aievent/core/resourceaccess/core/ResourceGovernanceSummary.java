package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;

public record ResourceGovernanceSummary(
        long activeGrants,
        long grantsExpiringSoon,
        long activeDenies,
        long openOrphans,
        long overdueReviewItems,
        long activeReviewCampaigns,
        long shadowMismatches24h,
        long staleDescriptors,
        Instant measuredAt) {
    public ResourceGovernanceSummary {
        if (activeGrants < 0 || grantsExpiringSoon < 0 || activeDenies < 0 || openOrphans < 0
                || overdueReviewItems < 0 || activeReviewCampaigns < 0 || shadowMismatches24h < 0 || staleDescriptors < 0)
            throw new IllegalArgumentException("governance counts must not be negative");
        if (measuredAt == null) throw new IllegalArgumentException("measuredAt is required");
    }
}
