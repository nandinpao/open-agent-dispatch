package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Immutable revision evidence for a semantic plan. */
public record ExecutionPlanRevision(
        String tenantId,
        String planId,
        int revision,
        String proposalId,
        Map<String,Object> snapshot,
        List<ExecutionPlanStep> steps,
        String changeReason,
        String actorRef,
        OffsetDateTime createdAt) {
    public ExecutionPlanRevision {
        snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
