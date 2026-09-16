package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 6 UNKNOWN-problem input. This contract intentionally contains no target Domain/System/Pool/Agent,
 * no provider selection and no A2A/MCP/Netty transport hint.
 */
public record TriageRequest(
        String requestId,
        String tenantId,
        String taskRef,
        String serviceCode,
        String problemStatement,
        List<String> contextRefs,
        Map<String, Object> inputContext,
        String dataClassification,
        String status,
        OffsetDateTime createdAt) {
    public TriageRequest {
        contextRefs = contextRefs == null ? List.of() : List.copyOf(contextRefs);
        inputContext = inputContext == null ? Map.of() : Map.copyOf(inputContext);
    }
}
