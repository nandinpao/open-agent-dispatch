package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Phase 6 confidence/human-review policy. Thresholds are tenant configuration, not hard-coded routing assumptions. */
public record TriagePolicy(
        String tenantId,
        String policyId,
        String displayName,
        double minClassificationConfidence,
        double minCapabilityResolutionConfidence,
        int maxCapabilitySuggestions,
        boolean requireHumanReviewOnCapabilityGap,
        String status,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public TriagePolicy {
        if (minClassificationConfidence < 0.0d || minClassificationConfidence > 1.0d) throw new IllegalArgumentException("minClassificationConfidence must be between 0 and 1");
        if (minCapabilityResolutionConfidence < 0.0d || minCapabilityResolutionConfidence > 1.0d) throw new IllegalArgumentException("minCapabilityResolutionConfidence must be between 0 and 1");
        if (maxCapabilitySuggestions < 1) throw new IllegalArgumentException("maxCapabilitySuggestions must be positive");
    }
}
