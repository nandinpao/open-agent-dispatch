package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.resourceaccess.contract.GovernedAccessRequestRecord;
import com.opensocket.aievent.core.resourceaccess.contract.ScopeGrantRecord;
import java.time.Instant;

/** Safe browser response. Canonical permission, role, scope evidence and deny evidence are intentionally omitted. */
public record UiAccessRequestResponse(
        String requestId,
        String uiActionId,
        String resourceType,
        String resourceId,
        long resourceVersionAtRequest,
        String requestedVisibility,
        Instant validFrom,
        Instant validTo,
        String state,
        long requestVersion,
        long grantVersion,
        boolean independentlyApproved,
        boolean requesterIsCurrentUser,
        Instant updatedAt) {
    static UiAccessRequestResponse from(GovernedAccessRequestRecord request, ScopeGrantRecord grant, String currentPrincipalId) {
        return new UiAccessRequestResponse(request.requestId(), request.uiActionId(), request.resourceType().name(),
                request.resourceId(), request.resourceVersionAtRequest(), request.requestedVisibility().name(),
                request.validFrom(), request.validTo(), request.state().name(), request.version(), grant.version(),
                !request.approvedBy().isBlank(), request.requesterId().equals(currentPrincipalId), request.updatedAt());
    }
}
