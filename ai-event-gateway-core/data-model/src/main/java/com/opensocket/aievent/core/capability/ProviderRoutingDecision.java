package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 4 WHO SHOULD decision evidence. selectedBindingId is a routing result only;
 * no execution adapter, transport, endpoint, credential or Agent Pool is represented.
 */
public record ProviderRoutingDecision(
        String decisionId,
        String tenantId,
        String decisionMode,
        String result,
        String capabilityCode,
        String operation,
        String routingProfileId,
        Integer routingProfileVersion,
        String selectedBindingId,
        String selectedProviderId,
        List<String> reasonCodes,
        List<ProviderRoutingCandidateScore> candidates,
        OffsetDateTime evaluatedAt) {
    public ProviderRoutingDecision {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
