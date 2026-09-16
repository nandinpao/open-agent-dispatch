package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Append-only Phase 6 semantic evidence. Accepted requirements remain WHAT only and are not authorization,
 * provider selection or execution decisions.
 */
public record TriageDecision(
        String decisionId,
        String tenantId,
        String requestId,
        String decisionMode,
        String result,
        String serviceCode,
        ProblemClassification classification,
        double classificationConfidence,
        double capabilityResolutionConfidence,
        String triagePolicyId,
        Integer triagePolicyVersion,
        List<CapabilityRequirement> acceptedRequirements,
        List<String> reasonCodes,
        boolean requiresHumanReview,
        OffsetDateTime decidedAt) {
    public TriageDecision {
        acceptedRequirements = acceptedRequirements == null ? List.of() : List.copyOf(acceptedRequirements);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
