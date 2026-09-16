package com.opensocket.aievent.core.capability;

import java.util.List;

/**
 * Phase 12 server-resolved requester context for runtime WHO MAY.
 * Task provenance identifies which principal must be re-resolved; this object represents current server-side state.
 */
public record RuntimeRequesterContext(
        String principalType,
        String principalId,
        String departmentId,
        List<String> groupIds,
        List<String> roleCodes,
        String sensitivityLevel,
        String siteId,
        String plantId,
        int delegationDepth,
        int agentCalls) {
    public RuntimeRequesterContext {
        groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }
}
