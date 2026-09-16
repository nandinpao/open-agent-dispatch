package com.opensocket.aievent.core.capability;

import java.util.List;

/**
 * Phase 4 admin preview. Candidate authority is referenced by persisted Phase 3 PASS decision IDs;
 * the caller cannot submit an authorized=true flag or a selected Provider.
 */
public record ProviderRoutingPreviewRequest(
        String capabilityCode,
        String operation,
        String routingProfileId,
        List<String> authorizationDecisionIds) {
    public ProviderRoutingPreviewRequest {
        authorizationDecisionIds = authorizationDecisionIds == null ? List.of() : List.copyOf(authorizationDecisionIds);
    }
}
