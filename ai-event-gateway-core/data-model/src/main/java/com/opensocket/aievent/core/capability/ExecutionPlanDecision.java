package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Append-only Phase 7 plan-validation evidence. PLAN_VALIDATED does not authorize execution. */
public record ExecutionPlanDecision(
        String decisionId,
        String tenantId,
        String requestId,
        String proposalId,
        String decisionMode,
        String result,
        String planId,
        Integer planRevision,
        String policyId,
        Integer policyVersion,
        Integer maxDepthObserved,
        Integer maxConcurrentBranchesObserved,
        List<String> reasonCodes,
        boolean requiresHumanReview,
        OffsetDateTime decidedAt) {
    public ExecutionPlanDecision { reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes); }
}
