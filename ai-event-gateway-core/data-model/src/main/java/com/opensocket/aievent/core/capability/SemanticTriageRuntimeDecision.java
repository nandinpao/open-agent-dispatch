package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Append-only Stage 10 evidence. It can never contain provider/agent/peer/binding selection. */
public record SemanticTriageRuntimeDecision(
        String runtimeDecisionId, String tenantId, String taskId, String triggerType,
        String flowMatchResult, String rolloutMode, String runtimePolicyId, Integer runtimePolicyVersion,
        String triageRequestId, String semanticDecisionId, String semanticResult, String action,
        String classificationCode, List<CapabilityRequirement> acceptedRequirements, String planningRequestId,
        String modelProfileRef, String promptProfileRef, boolean executionSideEffectAllowed,
        List<String> reasonCodes, Map<String,Object> evidence, OffsetDateTime decidedAt) {
    public SemanticTriageRuntimeDecision {
        acceptedRequirements = acceptedRequirements == null ? List.of() : List.copyOf(acceptedRequirements);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
