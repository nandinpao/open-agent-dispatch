package com.opensocket.aievent.core.capability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Explainable Phase 4 candidate result after WHO MAY and eligibility hard gates. */
public record ProviderRoutingCandidateScore(
        String bindingId,
        String providerId,
        String providerType,
        String authorizationDecisionId,
        String eligibilityObservationId,
        String eligibilityResult,
        List<String> exclusionReasons,
        Map<String, BigDecimal> scoreComponents,
        BigDecimal totalScore) {
    public ProviderRoutingCandidateScore {
        exclusionReasons = exclusionReasons == null ? List.of() : List.copyOf(exclusionReasons);
        scoreComponents = scoreComponents == null ? Map.of() : Map.copyOf(scoreComponents);
    }
}
