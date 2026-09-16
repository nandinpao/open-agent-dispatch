package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Phase 3 fail-closed WHO MAY decision. No provider ranking or transport selection is represented. */
public record DelegationAuthorizationDecision(
        String decisionId,
        String tenantId,
        String result,
        String capabilityCode,
        String operation,
        String bindingId,
        String providerId,
        String providerType,
        String selectedPolicyId,
        Integer selectedPolicyVersion,
        String approvalMode,
        List<String> reasonCodes,
        List<String> consideredPolicyIds,
        OffsetDateTime evaluatedAt) {

    public DelegationAuthorizationDecision {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        consideredPolicyIds = consideredPolicyIds == null ? List.of() : List.copyOf(consideredPolicyIds);
    }
}
