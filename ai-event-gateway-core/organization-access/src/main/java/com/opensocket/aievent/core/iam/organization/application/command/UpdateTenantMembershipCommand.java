package com.opensocket.aievent.core.iam.organization.application.command;

import java.time.Instant;

public record UpdateTenantMembershipCommand(
        String tenantId,
        String membershipId,
        String employeeId,
        Instant expiresAt,
        boolean defaultTenant,
        long expectedVersion,
        String reason,
        String actorId,
        String correlationId,
        String eventId) { }
