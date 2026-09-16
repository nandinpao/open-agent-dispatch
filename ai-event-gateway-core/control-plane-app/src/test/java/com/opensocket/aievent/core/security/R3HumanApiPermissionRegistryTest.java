package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class R3HumanApiPermissionRegistryTest {
    @Test
    void loadsCompleteGeneratedTargetOnlyPolicy() {
        var registry = new R3HumanApiPermissionRegistry();

        assertThat(registry.size()).isEqualTo(717);
        assertThat(registry.permissionCodes()).hasSize(574);
    }

    @Test
    void tenantOnboardingRouteCarriesCanonicalTenantPathVariable() {
        var registry = new R3HumanApiPermissionRegistry();

        var resolved = registry.resolve(
                "POST", "/api/admin/access/tenants/tenant-a/user-onboarding").orElseThrow();

        assertThat(resolved.rule().permission()).isEqualTo("identity.user.create");
        assertThat(resolved.rule().scopeType()).isEqualTo(
                com.opensocket.aievent.core.iam.rbac.domain.ScopeType.TENANT);
        assertThat(resolved.pathVariables()).containsEntry("tenantId", "tenant-a");
        assertThat(resolved.resourceId()).isEqualTo("tenant-a");
    }

    @Test
    void scopedPeopleCollectionDefersFinalOrganizationScopeToCanonicalControllerGuard() {
        var registry = new R3HumanApiPermissionRegistry();

        var resolved = registry.resolve(
                "GET", "/api/admin/access/tenants/tenant-a/users").orElseThrow();

        assertThat(resolved.rule().permission()).isEqualTo("identity.user.read");
        assertThat(resolved.rule().controllerScoped()).isTrue();
        assertThat(resolved.rule().scopeType()).isEqualTo(
                com.opensocket.aievent.core.iam.rbac.domain.ScopeType.TENANT);
    }

    @Test
    void tenantInvitationRouteCarriesCanonicalTenantPathVariable() {
        var registry = new R3HumanApiPermissionRegistry();

        var resolved = registry.resolve(
                "GET", "/api/admin/access/tenants/tenant-a/users/user-a/invitation").orElseThrow();

        assertThat(resolved.rule().permission()).isEqualTo("identity.user.read");
        assertThat(resolved.pathVariables())
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("userId", "user-a");
        assertThat(resolved.resourceId()).isEqualTo("user-a");
        assertThat(registry.resolve("GET", "/api/admin/access/users/user-a/invitation")).isEmpty();
    }

    @Test
    void resolvesCanonicalPermissionForRepresentativeAdminRoute() {
        var registry = new R3HumanApiPermissionRegistry();

        var resolved = registry.resolve("GET", "/admin/tasks/task-1").orElseThrow();

        assertThat(resolved.rule().permission()).isNotBlank();
        assertThat(resolved.rule().legacyRoles()).contains("VIEWER");
    }
    @Test
    void resolvesEncodedColonUiCapabilityBatchRoute() {
        var registry = new R3HumanApiPermissionRegistry();

        var resolved = registry.resolve(
                "POST", "/api/ui/list-capabilities%3Abatch").orElseThrow();

        assertThat(resolved.rule().permission()).isEqualTo("api.ui.capability.list.rows");
        assertThat(R3HumanApiPermissionRegistry.canonicalRoutePath(
                "/api/ui/list-capabilities%3abatch"))
                .isEqualTo("/api/ui/list-capabilities:batch");
    }

}
