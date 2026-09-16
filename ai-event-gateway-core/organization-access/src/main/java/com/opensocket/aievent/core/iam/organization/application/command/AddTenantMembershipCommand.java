package com.opensocket.aievent.core.iam.organization.application.command;

import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import java.time.Instant;

public record AddTenantMembershipCommand(
        String membershipId,
        String tenantId,
        String userId,
        String employeeId,
        Instant expiresAt,
        MembershipStatus initialStatus,
        boolean defaultTenant,
        TenantMembershipSource membershipSource,
        String reason,
        String actorId,
        String correlationId,
        String eventId) {

    public AddTenantMembershipCommand(
            String membershipId,
            String tenantId,
            String userId,
            String employeeId,
            Instant expiresAt,
            String actorId,
            String correlationId,
            String eventId) {
        this(
                membershipId,
                tenantId,
                userId,
                employeeId,
                expiresAt,
                MembershipStatus.ACTIVE,
                false,
                TenantMembershipSource.ADMIN_CREATED,
                "Tenant Membership created",
                actorId,
                correlationId,
                eventId);
    }
}
