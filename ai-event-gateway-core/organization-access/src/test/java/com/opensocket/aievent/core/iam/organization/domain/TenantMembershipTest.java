package com.opensocket.aievent.core.iam.organization.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantMembershipTest {
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void suspensionRemovesDefaultTenantAndCanBeReactivated() {
        TenantMembership active = membership(MembershipStatus.ACTIVE, true, NOW.plusSeconds(3600));

        TenantMembership suspended = active.changeStatus(
                MembershipStatus.SUSPENDED, "admin", "temporary suspension", NOW.plusSeconds(1));
        TenantMembership reactivated = suspended.changeStatus(
                MembershipStatus.ACTIVE, "admin", "access restored", NOW.plusSeconds(2));

        assertFalse(suspended.defaultTenant());
        assertEquals(MembershipStatus.ACTIVE, reactivated.status());
        assertFalse(reactivated.defaultTenant());
    }

    @Test
    void removedMembershipIsTerminal() {
        TenantMembership removed = membership(MembershipStatus.ACTIVE, false, NOW.plusSeconds(3600))
                .changeStatus(MembershipStatus.REMOVED, "admin", "offboarded", NOW.plusSeconds(1));

        OrganizationDomainException error = assertThrows(
                OrganizationDomainException.class,
                () -> removed.changeStatus(MembershipStatus.ACTIVE, "admin", "invalid restore", NOW.plusSeconds(2)));

        assertEquals(OrganizationReasonCode.INVALID_STATUS_TRANSITION, error.reasonCode());
    }

    @Test
    void removedMembershipCanBeExplicitlyReadmittedWithoutChangingItsIdentity() {
        TenantMembership removed = membership(MembershipStatus.ACTIVE, false, NOW.plusSeconds(3600))
                .changeStatus(MembershipStatus.REMOVED, "admin", "offboarded", NOW.plusSeconds(1));

        TenantMembership readmitted = removed.readmit(
                "EMP-2",
                NOW.plusSeconds(7200),
                true,
                TenantMembershipSource.PLATFORM_PROVISIONING,
                "admin",
                "returning worker",
                NOW.plusSeconds(2));

        assertEquals(removed.membershipId(), readmitted.membershipId());
        assertEquals(removed.tenantId(), readmitted.tenantId());
        assertEquals(removed.userPrincipal(), readmitted.userPrincipal());
        assertEquals(MembershipStatus.ACTIVE, readmitted.status());
        assertTrue(readmitted.defaultTenant());
        assertEquals("EMP-2", readmitted.employeeId().orElseThrow());
        assertEquals(TenantMembershipSource.PLATFORM_PROVISIONING, readmitted.membershipSource());
        assertEquals(removed.version() + 1, readmitted.version());
    }

    @Test
    void explicitReadmissionRejectsASecondCurrentMembership() {
        TenantMembership active = membership(MembershipStatus.ACTIVE, false, NOW.plusSeconds(3600));

        OrganizationDomainException error = assertThrows(
                OrganizationDomainException.class,
                () -> active.readmit(
                        "EMP-2", null, false, TenantMembershipSource.ADMIN_CREATED,
                        "admin", "duplicate admission", NOW.plusSeconds(1)));

        assertEquals(OrganizationReasonCode.TENANT_MEMBERSHIP_ALREADY_EXISTS, error.reasonCode());
    }

    @Test
    void expiredMembershipRequiresFutureExpiryBeforeActivation() {
        TenantMembership expired = membership(MembershipStatus.EXPIRED, false, NOW.minusSeconds(1));

        assertThrows(
                OrganizationDomainException.class,
                () -> expired.changeStatus(MembershipStatus.ACTIVE, "admin", "invalid activation", NOW));
    }


    @Test
    void invitedMembershipMayReceiveOrganizationAssignmentsWithoutBecomingActive() {
        TenantMembership invited = membership(MembershipStatus.INVITED, false, NOW.plusSeconds(3600));

        assertTrue(invited.organizationAssignableAt(NOW));
        assertFalse(invited.activeAt(NOW));
    }

    @Test
    void suspendedAndExpiredMembershipsCannotReceiveOrganizationAssignments() {
        TenantMembership suspended = membership(MembershipStatus.SUSPENDED, false, NOW.plusSeconds(3600));
        TenantMembership invitedButExpired = membership(MembershipStatus.INVITED, false, NOW.minusSeconds(1));

        assertFalse(suspended.organizationAssignableAt(NOW));
        assertFalse(invitedButExpired.organizationAssignableAt(NOW));
    }

    @Test
    void activeFutureMembershipCanBeDefaultTenant() {
        TenantMembership membership = membership(MembershipStatus.ACTIVE, true, NOW.plusSeconds(3600));

        assertTrue(membership.defaultTenant());
        assertTrue(membership.activeAt(NOW));
    }

    private static TenantMembership membership(
            MembershipStatus status,
            boolean defaultTenant,
            Instant expiresAt) {
        return TenantMembership.create(
                new MembershipId("membership-1"),
                new TenantId("tenant-a"),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a"),
                status,
                "EMP-1",
                NOW.minusSeconds(60),
                expiresAt,
                defaultTenant,
                TenantMembershipSource.ADMIN_CREATED,
                "admin",
                "test");
    }
}
