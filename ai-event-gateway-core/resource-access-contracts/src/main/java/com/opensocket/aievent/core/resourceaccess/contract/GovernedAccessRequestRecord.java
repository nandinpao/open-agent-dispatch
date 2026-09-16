package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Immutable request metadata linked to the canonical Scope Grant lifecycle. */
public record GovernedAccessRequestRecord(
        String tenantId,
        String requestId,
        String scopeGrantId,
        String uiActionId,
        ResourceType resourceType,
        String resourceId,
        long resourceVersionAtRequest,
        VisibilityLevel requestedVisibility,
        String requesterId,
        String businessPurpose,
        Instant validFrom,
        Instant validTo,
        GovernedAccessRequestState state,
        String approvedBy,
        String idempotencyKey,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    public GovernedAccessRequestRecord {
        tenantId = required(tenantId, "tenantId");
        requestId = required(requestId, "requestId");
        scopeGrantId = required(scopeGrantId, "scopeGrantId");
        uiActionId = required(uiActionId, "uiActionId");
        Objects.requireNonNull(resourceType, "resourceType");
        resourceId = required(resourceId, "resourceId");
        if (resourceVersionAtRequest < 1) throw new IllegalArgumentException("resourceVersionAtRequest must be positive");
        Objects.requireNonNull(requestedVisibility, "requestedVisibility");
        if (requestedVisibility == VisibilityLevel.NONE) throw new IllegalArgumentException("requestedVisibility must not be NONE");
        requesterId = required(requesterId, "requesterId");
        businessPurpose = required(businessPurpose, "businessPurpose");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validTo, "validTo");
        if (!validTo.isAfter(validFrom)) throw new IllegalArgumentException("validTo must be after validFrom");
        Objects.requireNonNull(state, "state");
        approvedBy = approvedBy == null ? "" : approvedBy.trim();
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (state == GovernedAccessRequestState.ACTIVE && approvedBy.isEmpty())
            throw new IllegalArgumentException("active request requires approver evidence");
        if (!approvedBy.isEmpty() && approvedBy.equals(requesterId))
            throw new IllegalArgumentException("requester cannot approve the same request");
    }
    public ResourceRef resourceRef() { return new ResourceRef(tenantId, resourceType, resourceId); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
