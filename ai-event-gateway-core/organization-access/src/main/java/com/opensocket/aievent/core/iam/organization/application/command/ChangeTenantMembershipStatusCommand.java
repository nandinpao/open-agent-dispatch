package com.opensocket.aievent.core.iam.organization.application.command;

import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;

public record ChangeTenantMembershipStatusCommand(
        String tenantId,
        String membershipId,
        MembershipStatus targetStatus,
        long expectedVersion,
        String reason,
        String actorId,
        String correlationId,
        String eventId) { }
