package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Phase 7 tenant policy governing semantic plan shape and budget, not Provider authorization. */
public record ExecutionPlanPolicy(
        String tenantId,
        String policyId,
        String displayName,
        int maxSteps,
        int maxPlanDepth,
        int maxConcurrentBranches,
        int maxCapabilityInvocations,
        Long maxTokenBudget,
        Double maxEstimatedCost,
        Long maxExecutionTimeSeconds,
        boolean requireHumanReviewOnPlanChange,
        String status,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
