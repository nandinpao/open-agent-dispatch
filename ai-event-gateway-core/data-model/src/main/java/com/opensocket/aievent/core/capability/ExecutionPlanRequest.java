package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Planning input built from Task/Triage WHAT evidence. */
public record ExecutionPlanRequest(
        String requestId,
        String tenantId,
        String taskRef,
        String sourceTriageDecisionId,
        String classificationCode,
        List<CapabilityRequirement> initialRequirements,
        List<String> contextRefs,
        String status,
        OffsetDateTime createdAt) {
    public ExecutionPlanRequest {
        initialRequirements = initialRequirements == null ? List.of() : List.copyOf(initialRequirements);
        contextRefs = contextRefs == null ? List.of() : List.copyOf(contextRefs);
    }
}
