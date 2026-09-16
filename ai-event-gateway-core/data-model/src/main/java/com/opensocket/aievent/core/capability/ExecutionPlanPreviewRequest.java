package com.opensocket.aievent.core.capability;

import java.util.List;

/** Admin/runtime planning request. It contains semantic WHAT/context only. */
public record ExecutionPlanPreviewRequest(
        String taskRef,
        String sourceTriageDecisionId,
        String classificationCode,
        List<CapabilityRequirement> initialRequirements,
        List<String> contextRefs) {
    public ExecutionPlanPreviewRequest {
        initialRequirements = initialRequirements == null ? List.of() : List.copyOf(initialRequirements);
        contextRefs = contextRefs == null ? List.of() : List.copyOf(contextRefs);
    }
}
