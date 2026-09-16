package com.opensocket.aievent.core.capability;

import java.util.List;

/** Proposed plan amendment. OpenDispatch must validate before creating a new immutable revision. */
public record ExecutionPlanAmendmentRequest(
        String proposerType,
        String proposerRef,
        List<ExecutionPlanStep> steps,
        String rationale,
        String changeReason) {
    public ExecutionPlanAmendmentRequest { steps = steps == null ? List.of() : List.copyOf(steps); }
}
