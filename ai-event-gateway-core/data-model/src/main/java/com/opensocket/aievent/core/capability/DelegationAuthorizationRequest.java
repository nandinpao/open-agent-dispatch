package com.opensocket.aievent.core.capability;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 3 authorization evaluation input.
 *
 * <p>The HTTP admin endpoint uses this only as an explicit governance preview contract.
 * Future runtime callers must build requester identity from authenticated server state,
 * not from untrusted request JSON.</p>
 */
public record DelegationAuthorizationRequest(
        CapabilityRequirement requirement,
        String bindingId,
        String requesterPrincipalType,
        String requesterDepartmentId,
        List<String> requesterGroupIds,
        List<String> requesterRoleCodes,
        String accessMode,
        String sensitivityLevel,
        BigDecimal estimatedCost,
        Integer delegationDepth,
        Integer agentCalls,
        Long executionTimeMs,
        Integer approvalCount) {

    public DelegationAuthorizationRequest {
        requesterGroupIds = requesterGroupIds == null ? List.of() : List.copyOf(requesterGroupIds);
        requesterRoleCodes = requesterRoleCodes == null ? List.of() : List.copyOf(requesterRoleCodes);
    }
}
