package com.opensocket.aievent.core.iam.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record TenantMembershipEvidence(
        String eventId,
        TenantId tenantId,
        MembershipId membershipId,
        String userId,
        Optional<MembershipStatus> previousStatus,
        MembershipStatus currentStatus,
        String reason,
        String actorId,
        String correlationId,
        long membershipVersion,
        Instant occurredAt) {
    public TenantMembershipEvidence {
        eventId = OrganizationText.required(eventId, "eventId", 128);
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(membershipId, "membershipId");
        userId = OrganizationText.required(userId, "userId", 128);
        previousStatus = previousStatus == null ? Optional.empty() : previousStatus;
        Objects.requireNonNull(currentStatus, "currentStatus");
        reason = OrganizationText.required(reason, "reason", 500);
        actorId = OrganizationText.required(actorId, "actorId", 128);
        correlationId = OrganizationText.optional(correlationId, 128);
        if (membershipVersion < 1) throw new IllegalArgumentException("membershipVersion must be positive");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
