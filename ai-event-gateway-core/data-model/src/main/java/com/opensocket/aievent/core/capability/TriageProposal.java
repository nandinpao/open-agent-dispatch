package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 6 Agent/Human semantic proposal. It is never executable authority. OpenDispatch validates it against
 * Canonical Capability and Triage Policy before producing any accepted CapabilityRequirement.
 */
public record TriageProposal(
        String proposalId,
        String tenantId,
        String requestId,
        String proposerType,
        String proposerRef,
        ProblemClassification classification,
        double capabilityResolutionConfidence,
        List<TriageCapabilitySuggestion> capabilitySuggestions,
        List<String> investigationSuggestions,
        List<String> explanatoryTaxonomies,
        String rationale,
        OffsetDateTime proposedAt) {
    public TriageProposal {
        if (capabilityResolutionConfidence < 0.0d || capabilityResolutionConfidence > 1.0d) throw new IllegalArgumentException("capabilityResolutionConfidence must be between 0 and 1");
        capabilitySuggestions = capabilitySuggestions == null ? List.of() : List.copyOf(capabilitySuggestions);
        investigationSuggestions = investigationSuggestions == null ? List.of() : List.copyOf(investigationSuggestions);
        explanatoryTaxonomies = explanatoryTaxonomies == null ? List.of() : List.copyOf(explanatoryTaxonomies);
    }
}
