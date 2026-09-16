package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Map;

/** Request used to choose exact known fast path versus TRIAGE_REQUIRED; it cannot select an executor. */
public record TriagePreviewRequest(
        String taskRef,
        String serviceCode,
        String problemStatement,
        List<String> contextRefs,
        Map<String, Object> inputContext,
        String dataClassification) {
    public TriagePreviewRequest {
        contextRefs = contextRefs == null ? List.of() : List.copyOf(contextRefs);
        inputContext = inputContext == null ? Map.of() : Map.copyOf(inputContext);
    }
}
