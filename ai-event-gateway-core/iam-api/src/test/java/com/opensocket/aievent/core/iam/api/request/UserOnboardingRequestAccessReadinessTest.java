package com.opensocket.aievent.core.iam.api.request;

import static org.junit.jupiter.api.Assertions.*;

import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserOnboardingRequestAccessReadinessTest {
    @Test
    void adminCreatedActivePersonRequiresInitialResponsibilityUnlessAccessIsExplicitlyDeferred() {
        var error = assertThrows(IllegalArgumentException.class, () -> request(List.of(), false));
        assertEquals("IDENTITY_INITIAL_RESPONSIBILITY_REQUIRED", error.getMessage());
    }

    @Test
    void explicitNoApplicationAccessAllowsEmptyRoleBindings() {
        var request = request(List.of(), true);
        assertTrue(request.roles().isEmpty());
        assertTrue(request.applicationAccessDeferred());
    }

    @Test
    void deferredAccessCannotAlsoCarryRoleAssignment() {
        var role = new UserOnboardingRequest.RoleAssignment(
                "binding-1", "role-viewer", "TENANT", "tenant-a", null, null);
        var error = assertThrows(IllegalArgumentException.class, () -> request(List.of(role), true));
        assertEquals("IDENTITY_APPLICATION_ACCESS_DEFERRED_WITH_ROLE", error.getMessage());
    }

    @Test
    void responsibilityAssignmentCreatesNormalAccessReadyOnboardingRequest() {
        var role = new UserOnboardingRequest.RoleAssignment(
                "binding-1", "role-viewer", "TENANT", "tenant-a", null, null);
        var request = request(List.of(role), false);
        assertEquals(1, request.roles().size());
        assertFalse(request.applicationAccessDeferred());
    }

    private static UserOnboardingRequest request(
            List<UserOnboardingRequest.RoleAssignment> roles, boolean deferred) {
        return new UserOnboardingRequest(
                null, "rbac.test", "rbac.test@example.com", "RBAC Test",
                UserCreationMode.ADMIN_CREATED, "LOCAL", "MANUAL", "Temporary-Only-2026!",
                "membership-1", MembershipStatus.ACTIVE, null, null, true,
                TenantMembershipSource.ADMIN_CREATED, List.of(), List.of(), roles, deferred,
                "RBAC certification account");
    }
}
