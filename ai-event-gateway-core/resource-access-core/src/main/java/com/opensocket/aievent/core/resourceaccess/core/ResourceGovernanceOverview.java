package com.opensocket.aievent.core.resourceaccess.core;

import java.time.Instant;
import java.util.List;

public record ResourceGovernanceOverview(
        String resourceType, String resourceId, String resourceKey,
        String ownerDepartmentId, String ownerGroupId, String stewardUserId,
        String requesterDepartmentId, String executorDepartmentId,
        String sensitivityLevel, String maximumVisibility, String visibilityPolicyId,
        String securityState, String projectionStatus, long resourceVersion,
        long participantVersion, long activeGrantCount, long activeDenyCount,
        String accessReviewStatus, List<ResourceParticipantView> participants,
        Instant sourceResolvedAt, Instant lastProjectedAt, Instant lastReconciledAt) {
    public ResourceGovernanceOverview { participants = participants == null ? List.of() : List.copyOf(participants); }
}
