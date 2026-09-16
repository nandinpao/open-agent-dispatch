package com.opensocket.aievent.core.iam.api.response;

/** Aggregated Tenant workspace summary used by the Phase 4 administration landing page. */
public record TenantWorkspaceSummaryResponse(
        String tenantId,
        long activePeople,
        long tenantAdministratorCount,
        long pendingInvitations,
        long signInSetupRequiredCount,
        long mfaEnrolledPeople,
        long suspendedPeople,
        long peopleWithoutDepartment,
        long departmentCount,
        long departmentsWithoutManager,
        long groupCount,
        long activeRoleCount,
        long activeBindingCount,
        long expiringBindingCount) {
}
