package com.opensocket.aievent.core.iam.api.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.opensocket.aievent.core.iam.api.response.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UiFeatureEntitlementServiceTest {
    private final UiFeatureEntitlementService service = new UiFeatureEntitlementService();

    @Test
    void tenantUserSeesOnlyFeaturesBackedByEffectivePermissions() {
        var result = service.project(session(Set.of("USER"), Set.of("api.task.search", "identity.user.read"), "tenant-a"));
        var routes = result.navigation().stream().map(item -> item.route()).collect(java.util.stream.Collectors.toSet());
        assertTrue(routes.contains("/tasks"));
        assertTrue(routes.contains("/admin/tenants/{tenantId}"));
        assertFalse(routes.contains("/dispatch-flows"));
        assertFalse(routes.contains("/instance-administration"));
        assertTrue(result.pages().get("tasks").allowed());
        assertFalse(result.pages().get("dispatch").allowed());
    }

    @Test
    void a2aGovernanceProjectsRetiredDirectionalPoliciesAsReadOnlyArchive() {
        var result = service.project(session(Set.of("TENANT_ADMIN"), Set.of(
                "api.a2.agovernance.policies", "api.a2.agovernance.upsert.policy"),
                Map.of("api.a2.agovernance.upsert.policy", Set.of("TENANT:tenant-a")), "tenant-a"));
        var routes = result.navigation().stream().map(item -> item.route()).collect(java.util.stream.Collectors.toSet());
        assertFalse(routes.contains("/a2a-governance"));
        assertTrue(result.pages().get("a2a-governance").allowed());
        assertEquals("READ_ONLY", result.pages().get("a2a-governance").displayMode());
        assertFalse(result.actions().contains("a2a-policy.manage"));
        assertFalse(result.actionScopes().containsKey("a2a-policy.manage"));
    }

    @Test
    void accessActionsUseCanonicalPermissionProjection() {
        var result = service.project(session(Set.of("TENANT_ADMIN"), Set.of(
                "identity.user.read", "identity.user.create", "identity.department.manage",
                "identity.role_binding.manage"), "tenant-a"));
        assertTrue(result.actions().contains("access.people.create"));
        assertTrue(result.actions().contains("access.department.manage"));
        assertTrue(result.actions().contains("access.assignment.manage"));
        assertFalse(result.actions().contains("access.group.manage"));
        assertFalse(result.actions().contains("access.role.manage"));
    }


    @Test
    void runtimeConfigurationCutoverActionsRemainAtomicInUiProjection() {
        var viewer = service.project(session(Set.of("PLATFORM_OPERATOR"), Set.of(
                "configuration.view", "configuration.cutover.view"), ""));
        assertEquals("READ_ONLY", viewer.pages().get("runtime-configuration").displayMode());
        assertEquals("ENABLED", viewer.actionEntitlements().get("runtime-configuration.cutover.view").displayMode());
        assertEquals("HIDDEN", viewer.actionEntitlements().get("runtime-configuration.cutover.finalize").displayMode());

        var operator = service.project(session(Set.of("PLATFORM_ADMIN"), Set.of(
                "configuration.view", "configuration.cutover.view", "configuration.cutover.assess",
                "configuration.cutover.prepare", "configuration.cutover.finalize",
                "configuration.cutover.cancel", "configuration.cutover.certify"), ""));
        assertEquals("ENABLED", operator.pages().get("runtime-configuration").displayMode());
        assertEquals("ENABLED", operator.actionEntitlements().get("runtime-configuration.cutover.assess").displayMode());
        assertEquals("ENABLED", operator.actionEntitlements().get("runtime-configuration.cutover.prepare").displayMode());
        assertEquals("ENABLED", operator.actionEntitlements().get("runtime-configuration.cutover.finalize").displayMode());
        assertEquals("ENABLED", operator.actionEntitlements().get("runtime-configuration.cutover.cancel").displayMode());
        assertEquals("ENABLED", operator.actionEntitlements().get("runtime-configuration.cutover.certify").displayMode());
    }

    @Test
    void highRiskSettingsUseDedicatedFeatureAndActionProjection() {
        var viewer = service.project(session(Set.of("TENANT_OPERATOR"), Set.of(
                "admin.agent.assignment.capabilities", "release.certification.read"), "tenant-a"));
        assertEquals("READ_ONLY", viewer.pages().get("delegation-governance").displayMode());
        assertEquals("READ_ONLY", viewer.pages().get("provider-routing").displayMode());
        assertEquals("READ_ONLY", viewer.pages().get("release-certification").displayMode());
        assertEquals("HIDDEN", viewer.actionEntitlements().get("delegation-governance.manage").displayMode());
        assertEquals("HIDDEN", viewer.actionEntitlements().get("provider-routing.manage").displayMode());
        assertEquals("HIDDEN", viewer.actionEntitlements().get("release-certification.candidate.activate").displayMode());

        var manager = service.project(session(Set.of("TENANT_ADMIN"), Set.of(
                "admin.agent.assignment.capabilities", "admin.agent.assignment.upsert.capability",
                "release.certification.read", "release.candidate.create", "release.candidate.activate",
                "release.flow.promote"), "tenant-a"));
        assertEquals("ENABLED", manager.pages().get("delegation-governance").displayMode());
        assertEquals("ENABLED", manager.pages().get("provider-routing").displayMode());
        assertEquals("ENABLED", manager.pages().get("release-certification").displayMode());
        assertEquals("ENABLED", manager.actionEntitlements().get("delegation-governance.manage").displayMode());
        assertEquals("ENABLED", manager.actionEntitlements().get("provider-routing.manage").displayMode());
        assertEquals("ENABLED", manager.actionEntitlements().get("release-certification.candidate.activate").displayMode());
        assertEquals("ENABLED", manager.actionEntitlements().get("release-certification.flow.promote").displayMode());
        assertTrue(manager.navigation().stream().anyMatch(item -> item.featureId().equals("administration")));
    }

    @Test
    void actionScopesPreserveCanonicalTenantAndDepartmentAuthority() {
        var result = service.project(session(
                Set.of("DEPARTMENT_ADMIN"),
                Set.of("identity.department.manage", "identity.membership.manage"),
                Map.of(
                        "identity.department.manage", Set.of("DEPARTMENT_SUBTREE:dept-it"),
                        "identity.membership.manage", Set.of("DEPARTMENT:dept-it")),
                "tenant-a"));
        assertEquals(Set.of("DEPARTMENT_SUBTREE:dept-it"), result.actionScopes().get("access.department.manage"));
        assertEquals(Set.of("DEPARTMENT:dept-it"), result.actionScopes().get("access.membership.manage"));
        assertFalse(result.actionScopes().get("access.department.manage").contains("TENANT:tenant-a"));
    }

    @Test
    void internalEngineeringFeaturesNeverAppearInNormalNavigation() {
        var result = service.project(session(Set.of("INSTANCE_ROOT"), Set.of(
                "permission.catalog.read", "permission.enforcement_cutover.read", "permission.entry_point.read"), ""));
        var ids = result.navigation().stream().map(item -> item.featureId()).collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.contains("instance-administration"));
        assertFalse(ids.contains("permission-catalog"));
        assertFalse(ids.contains("enforcement-activation"));
        assertFalse(ids.contains("permission-readiness"));
        assertTrue(result.pages().get("permission-catalog").allowed());
    }

    @Test
    void instanceRootGetsTenantCrudActionsWhenAddressingTenantAdministration() {
        var result = service.project(session(Set.of("INSTANCE_ROOT"), Set.of(), ""));
        assertTrue(result.actions().contains("access.people.create"));
        assertTrue(result.actions().contains("access.people.update"));
        assertTrue(result.actions().contains("access.department.manage"));
        assertTrue(result.actions().contains("access.group.manage"));
        assertTrue(result.actions().contains("access.role.manage"));
        assertTrue(result.actions().contains("access.role-permissions.manage"));
        assertTrue(result.actions().contains("access.assignment.manage"));
        assertEquals(Set.of("*"), result.actionScopes().get("access.people.create"));
        assertEquals(Set.of("*"), result.actionScopes().get("access.department.manage"));
    }

    @Test
    void delegatedPlatformAdministratorUsesInstancePermissionsWithoutReceivingRootEngineeringAccess() {
        var result = service.project(session(Set.of("PLATFORM_ADMIN"), Set.of(
                "instance.tenant.read", "identity.platform_user.read",
                "permission.catalog.read", "permission.enforcement_cutover.read", "permission.entry_point.read"), "tenant-a"));
        assertTrue(result.pages().get("instance-administration").allowed());
        assertTrue(result.navigation().stream().anyMatch(item -> item.featureId().equals("instance-administration")));
        assertFalse(result.pages().get("permission-catalog").allowed());
        assertFalse(result.pages().get("enforcement-activation").allowed());
        assertFalse(result.pages().get("permission-readiness").allowed());
    }

    @Test
    void tenantAdministratorCannotOpenPlatformOrInternalEngineeringPagesWithoutInstancePermissions() {
        var result = service.project(session(Set.of("TENANT_ADMIN"), Set.of(
                "permission.catalog.read", "permission.enforcement_cutover.read", "permission.entry_point.read"), "tenant-a"));
        assertFalse(result.pages().get("instance-administration").allowed());
        assertFalse(result.pages().get("permission-catalog").allowed());
        assertFalse(result.pages().get("enforcement-activation").allowed());
        assertFalse(result.pages().get("permission-readiness").allowed());
    }


    @Test
    void contract3ProjectsReadOnlyAndEnabledModesFromCanonicalActions() {
        var viewer = service.project(session(Set.of("IDENTITY_VIEWER"), Set.of(
                "identity.user.read", "identity.department.read", "identity.group.read",
                "identity.tenant_role.read", "identity.role_binding.read", "audit.identity.read"), "tenant-a"));
        assertEquals("3.0", viewer.contractVersion());
        assertEquals("READ_ONLY", viewer.pages().get("access-people").displayMode());
        assertEquals("READ_ONLY", viewer.pages().get("access-organization").displayMode());
        assertEquals("READ_ONLY", viewer.pages().get("access-governance").displayMode());
        assertEquals("READ_ONLY", viewer.pages().get("access-security").displayMode());
        assertEquals("HIDDEN", viewer.actionEntitlements().get("access.people.create").displayMode());

        var userAdmin = service.project(session(Set.of("USER_ADMIN"), Set.of(
                "identity.user.read", "identity.user.create", "identity.user.update",
                "identity.tenant_membership.manage", "identity.department.read", "identity.group.read"), "tenant-a"));
        assertEquals("ENABLED", userAdmin.pages().get("access-people").displayMode());
        assertEquals("READ_ONLY", userAdmin.pages().get("access-organization").displayMode());
        assertEquals("HIDDEN", userAdmin.pages().get("access-governance").displayMode());
        assertEquals("ENABLED", userAdmin.actionEntitlements().get("access.people.create").displayMode());
    }

    @Test
    void peopleAndAccessNavigatorChildrenAreProjectedByBackend() {
        var result = service.project(session(Set.of("ACCESS_ADMIN"), Set.of(
                "identity.user.read", "identity.department.read", "identity.group.read",
                "identity.tenant_role.read", "identity.tenant_role.manage",
                "identity.role_binding.read", "identity.role_binding.manage"), "tenant-a"));
        var parent = result.navigation().stream()
                .filter(item -> item.featureId().equals("access-management"))
                .findFirst().orElseThrow();
        assertEquals("ENABLED", parent.displayMode());
        assertEquals(List.of("access-overview", "access-people", "access-organization", "access-governance"),
                parent.children().stream().map(item -> item.featureId()).toList());
        assertEquals("/admin/tenants/{tenantId}/access",
                parent.children().stream().filter(item -> item.featureId().equals("access-governance"))
                        .findFirst().orElseThrow().route());
    }

    @Test
    void hiddenPagesDoNotLeakIntoNestedNavigation() {
        var result = service.project(session(Set.of("USER_ADMIN"), Set.of(
                "identity.user.read", "identity.user.create", "identity.department.read", "identity.group.read"), "tenant-a"));
        var parent = result.navigation().stream()
                .filter(item -> item.featureId().equals("access-management"))
                .findFirst().orElseThrow();
        assertFalse(parent.children().stream().anyMatch(item -> item.featureId().equals("access-governance")));
        assertFalse(parent.children().stream().anyMatch(item -> item.featureId().equals("access-security")));
    }

    @Test
    void responsibilityUiAccessPreviewUsesTheSameRegistryAsSessionProjection() {
        Set<String> permissions = Set.of(
                "identity.user.read", "identity.user.create", "identity.department.read", "identity.group.read");
        var sessionProjection = service.project(session(Set.of("USER_ADMIN"), permissions, "tenant-a"));
        var preview = service.previewTenant("tenant-a", permissions, Map.of());
        assertEquals(sessionProjection.pages().get("access-people").displayMode(), preview.pages().get("access-people").displayMode());
        assertEquals(sessionProjection.pages().get("access-organization").displayMode(), preview.pages().get("access-organization").displayMode());
        assertEquals(sessionProjection.actionEntitlements().get("access.people.create").displayMode(), preview.actionEntitlements().get("access.people.create").displayMode());
    }

    @Test
    void effectiveUiExplanationKeepsCanonicalRoleBindingSource() {
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        var source = new EffectiveAccessSourceResponse(
                "binding-1", "role-user-admin", "User Administrator", "GROUP", "group-it",
                "DEPARTMENT", "dept-it", now, null, true);
        var effectiveAccess = new EffectiveAccessResponse(
                "tenant-a", "user-1", now,
                List.of(
                        new EffectivePermissionResponse("identity.user.read", List.of(source), List.of()),
                        new EffectivePermissionResponse("identity.user.create", List.of(source), List.of())),
                List.of());
        var authority = new IamEffectiveAuthorityResponse(
                "tenant-a", "user-1", now, Set.of("USER_ADMIN"),
                Set.of("identity.user.read", "identity.user.create"), Map.of(), effectiveAccess);
        var explanation = service.explainEffectiveAccess("tenant-a", authority);
        assertEquals("ENABLED", explanation.uiAccess().pages().get("access-people").displayMode());
        assertTrue(explanation.pageReasons().get("access-people").stream()
                .anyMatch(reason -> reason.roleName().equals("User Administrator") && reason.inheritedFromGroup()));
        assertTrue(explanation.actionReasons().get("access.people.create").stream()
                .anyMatch(reason -> reason.bindingId().equals("binding-1")));
    }


    @Test
    void authenticatedPersonAlwaysGetsMyAccountEvenWithoutBusinessPermissions() {
        var result = service.project(session(Set.of(), Set.of(), "tenant-a"));
        assertEquals("READ_ONLY", result.pages().get("my-account").displayMode());
        assertEquals(List.of("my-account"), result.navigation().stream().map(item -> item.featureId()).toList());
    }

    @Test
    void responsibilityPreviewDoesNotPretendSelfServiceIsRoleGranted() {
        var preview = service.previewTenant("tenant-a", Set.of(), Map.of());
        assertEquals("HIDDEN", preview.pages().get("my-account").displayMode());
        assertTrue(preview.navigation().isEmpty());
    }

    private static IamUiSessionResponse session(Set<String> roles, Set<String> permissions, String tenantId) {
        return session(roles, permissions, Map.of(), tenantId);
    }

    private static IamUiSessionResponse session(Set<String> roles, Set<String> permissions, Map<String, Set<String>> permissionScopes, String tenantId) {
        return new IamUiSessionResponse(
                "CANONICAL_SESSION", "user-1", "user", "User", roles, permissions, permissionScopes, tenantId,
                List.of(), Set.of(), 1L, Instant.parse("2026-08-08T00:00:00Z"), Instant.parse("2026-08-08T01:00:00Z"), Set.of("PASSWORD", "TOTP"));
    }
}
