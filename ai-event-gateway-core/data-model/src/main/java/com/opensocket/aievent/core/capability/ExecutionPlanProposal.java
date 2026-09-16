package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Planner/Human proposal evidence. It is never executable authority. */
public record ExecutionPlanProposal(
        String proposalId,
        String tenantId,
        String requestId,
        String proposerType,
        String proposerRef,
        List<ExecutionPlanStep> steps,
        String rationale,
        OffsetDateTime proposedAt) {
    public ExecutionPlanProposal { steps = steps == null ? List.of() : List.copyOf(steps); }
}
