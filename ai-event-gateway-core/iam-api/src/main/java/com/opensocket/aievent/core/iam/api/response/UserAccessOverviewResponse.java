package com.opensocket.aievent.core.iam.api.response;

import java.util.List;

/** Unified detail projection used by the R5 Admin UI without switching products or identity sources. */
public record UserAccessOverviewResponse(
        String tenantId,
        UserResponse user,
        List<MembershipResponse> memberships,
        EffectiveAccessResponse effectiveAccess,
        UserAuthenticationReadinessResponse authenticationReadiness,
        List<SessionResponse> sessions,
        MachineOwnershipImpactResponse machineOwnershipImpact) {
}
