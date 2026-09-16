package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Current view of a versioned semantic plan. SEMANTICALLY_VALIDATED is not WHO MAY authorization. */
public record ExecutionPlan(
        String planId,
        String tenantId,
        String requestId,
        String taskRef,
        String sourceTriageDecisionId,
        String classificationCode,
        String policyId,
        int policyVersion,
        String status,
        int currentRevision,
        ExecutionPlanBudget budget,
        List<ExecutionPlanStep> steps,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public ExecutionPlan { steps = steps == null ? List.of() : List.copyOf(steps); }
}
