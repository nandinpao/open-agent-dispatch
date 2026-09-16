package com.opensocket.aievent.core.iam.organization.application.command;

import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembershipType;
import java.time.Instant;

public record UpdateDepartmentMembershipCommand(
        String tenantId,
        String membershipId,
        DepartmentMembershipType membershipType,
        boolean primary,
        Instant expiresAt,
        boolean remove,
        String replacementMembershipId,
        Long replacementExpectedVersion,
        long expectedVersion,
        String actorId,
        String correlationId,
        String eventId) { }
