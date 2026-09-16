package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.response.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application-owned UI feature registry.
 *
 * Feature metadata belongs to the product, not to Tenant-admin CRUD. The service
 * projects that registry through the canonical effective permissions already used
 * by API authorization/session projection. No frontend Role checks are authoritative.
 *
 * Contract 3.0 adds explicit presentation modes and nested Navigator metadata. It
 * does not create a second Role-to-Menu authorization model.
 */
public final class UiFeatureEntitlementService {
    private static final String TENANT = "TENANT";
    private static final String PLATFORM = "PLATFORM";
    private static final String INTERNAL = "INTERNAL_ENGINEERING";

    private static final String HIDDEN = "HIDDEN";
    private static final String READ_ONLY = "READ_ONLY";
    private static final String ENABLED = "ENABLED";

    private static final List<Feature> FEATURES = List.of(
            selfServiceFeature("my-account", null, "ACCOUNT", 5, "/account", "My Account",
                    "Review your sign-in identity and account security without granting business workspace access."),
            feature("dashboard", null, "OPERATIONS", 10, "/dashboard", "Dashboard",
                    "Review dispatch readiness, Agent health and blocked work.", TENANT, true,
                    any("admin.core.dashboard.snapshot", "api.task.search", "admin.agent.governance.search.agents", "resource.governance.read"),
                    action("dashboard.analytics.view", "resource.governance.read")),
            feature("source-systems", null, "DISPATCH", 20, "/source-systems", "Source Systems",
                    "Manage systems that send work to OpenDispatch.", TENANT, true,
                    any("admin.source.system.list"),
                    action("source-systems.create", "admin.source.system.create"),
                    action("source-systems.update", "admin.source.system.update"),
                    action("source-systems.retire", "admin.source.system.retire")),
            feature("dispatch", null, "DISPATCH", 30, "/dispatch-flows", "Dispatch",
                    "Configure Source Flows, Agent Pools and governed routing.", TENANT, true,
                    any("admin.dispatch.flow.list"),
                    action("dispatch.create", "admin.dispatch.flow.create"),
                    action("dispatch.update", "admin.dispatch.flow.update"),
                    action("dispatch.retire", "admin.dispatch.flow.retire"),
                    action("dispatch.cross-scope", "admin.dispatch.cross.scope.manage")),
            feature("a2a-governance", null, "DISPATCH", 35, "/a2a-governance", "Legacy A2A Archive",
                    "Review retired directional cross-Agent policy evidence. Current execution uses capability-first governed delegation.", TENANT, false,
                    any("a2a.policy.read", "api.a2.agovernance.policies")),
            feature("agents", null, "OPERATIONS", 40, "/agents", "Agents",
                    "Review Agent identity, readiness and governed lifecycle.", TENANT, true,
                    any("admin.agent.governance.search.agents"),
                    action("agents.setup", "admin.agent.setup.setup.agent"),
                    action("agents.approve", "admin.agent.governance.approve.agent"),
                    action("agents.update", "admin.agent.governance.update.agent"),
                    action("agents.credentials", "admin.agent.governance.issue.credential"),
                    action("agents.capabilities", "admin.agent.assignment.agent.capabilities", "admin.agent.assignment.request.agent.capability"),
                    action("agents.runtime", "admin.agent.assignment.runtime.bindings", "admin.agent.governance.disconnect.agent.runtime")),
            feature("tasks", null, "OPERATIONS", 50, "/tasks", "Tasks",
                    "Review work, assignment evidence and operational failures.", TENANT, true,
                    any("task.read"),
                    action("tasks.execute", "task.execute"),
                    action("tasks.approve", "task.approve"),
                    action("tasks.update", "task.update"),
                    action("tasks.operate", "api.task.cancel", "api.task.reassign", "api.task.timeout",
                            "admin.core.admin.task.facade.manual.retry.task", "admin.core.admin.task.facade.escalate.task")),
            feature("a2a-operations", null, "OPERATIONS", 60, "/a2a-operations", "Delegations",
                    "Review governed cross-Agent requests and results.", TENANT, true,
                    any("api.a2.aoperations.search")),
            feature("issues-events", null, "OPERATIONS", 70, "/issues-events", "Issues & Events",
                    "Review issue projections, failures and security/runtime evidence.", TENANT, true,
                    any("admin.business.event.list", "integration.issue.link.read", "api.issue.projection.issues", "api.integration.identity.connections", "audit.read"),
                    action("issues-events.operate", "api.issue.projection.resolve", "api.issue.projection.retry",
                            "api.incident.resolve", "api.incident.reopen", "api.incident.suppress")),
            feature("business-events", "issues-events", "OPERATIONS", 710, "/issues-events/business-events", "Business Events",
                    "Review Source-scoped Event metadata and separately authorized payload evidence.", TENANT, true,
                    any("admin.business.event.list"),
                    action("business-events.payload", "admin.business.event.payload.read")),
            feature("business-issues", "issues-events", "OPERATIONS", 720, "/issues-events/business-issues", "Business Issues",
                    "Review Task-scoped Issue projections without inheriting Jira/Redmine provider ACLs.", TENANT, true,
                    any("integration.issue.link.read"),
                    action("business-issues.update", "integration.issue.link.update"),
                    action("business-issues.attachments", "integration.issue.attachment.read"),
                    action("business-issues.attachment-download", "integration.issue.attachment.download"),
                    action("business-issues.export", "integration.issue.export")),
            feature("operations", null, "OPERATIONS", 80, "/operations", "Advanced Operations",
                    "Review governed exports, background authorization and audit diagnostics.", TENANT, false,
                    any("audit.read")),
            feature("sync-operations", null, "OPERATIONS", 90, "/operations/integration-sync", "Sync Operations",
                    "Operate Issue projection queues, Webhooks, conflicts and Dead Letters.", TENANT, false,
                    any("integration.sync_operations.read")),
            feature("integrations", null, "ADMINISTRATION", 110, "/settings/integrations", "Integrations",
                    "Configure provider connections, credentials and project mappings.", TENANT, true,
                    any("api.integration.identity.connections", "integration.issue.connection.read"),
                    action("integrations.manage", "integration.issue.connection.update")),
            feature("administration", null, "ADMINISTRATION", 120, "/settings", "Settings",
                    "Manage common environment, security and governance settings.", TENANT, true,
                    any("security.policy.read", "api.integration.identity.connections", "admin.agent.governance.search.enrollments"),
                    action("settings.security.manage", "security.policy.manage")),

            // People & Access is one top-level Navigator entry with backend-owned child areas.
            feature("access-management", null, "ADMINISTRATION", 130, "/admin/tenants/{tenantId}", "People & Access",
                    "Manage people, organization, responsibilities, sign-in security and audit from one workspace.", TENANT, true,
                    any("identity.platform_user.read", "identity.user.read", "instance.tenant.read",
                            "identity.department.read", "identity.group.read", "identity.tenant_role.read",
                            "identity.role_binding.read", "security.session.read", "audit.identity.read")),
            feature("access-overview", "access-management", "ADMINISTRATION", 1300, "/admin/tenants/{tenantId}", "Overview",
                    "See setup progress, risks and the next administration action.", TENANT, true,
                    any("identity.platform_user.read", "identity.user.read", "instance.tenant.read",
                            "identity.department.read", "identity.group.read", "identity.tenant_role.read",
                            "identity.role_binding.read", "security.session.read", "audit.identity.read")),
            feature("access-people", "access-management", "ADMINISTRATION", 1310, "/admin/tenants/{tenantId}/people", "People",
                    "People, membership and sign-in readiness.", TENANT, true,
                    any("identity.platform_user.read", "identity.user.read"),
                    action("access.people.create", "identity.user.create"),
                    action("access.people.update", "identity.user.update"),
                    action("access.tenant-membership.manage", "identity.tenant_membership.manage")),
            feature("access-organization", "access-management", "ADMINISTRATION", 1320, "/admin/tenants/{tenantId}/organization", "Organization",
                    "Departments, Groups, Managers and members.", TENANT, true,
                    any("identity.department.read", "identity.group.read"),
                    action("access.department.manage", "identity.department.manage"),
                    action("access.group.manage", "identity.group.manage"),
                    action("access.membership.manage", "identity.membership.manage")),
            feature("access-governance", "access-management", "ADMINISTRATION", 1330, "/admin/tenants/{tenantId}/access", "Access",
                    "Responsibilities, assignments and Effective Access.", TENANT, true,
                    any("identity.role_binding.read", "identity.tenant_role.read"),
                    action("access.role.manage", "identity.tenant_role.manage"),
                    action("access.role-permissions.manage", "identity.role_permission.manage"),
                    action("access.assignment.manage", "identity.role_binding.manage"),
                    action("access.approval.request", "identity.role_approval.request"),
                    action("access.approval.approve", "identity.role_approval.approve")),
            feature("access-security", "access-management", "ADMINISTRATION", 1340, "/admin/tenants/{tenantId}/security", "Security & Audit",
                    "Sessions, sign-in policy, credentials and audit.", TENANT, true,
                    any("security.session.read", "security.policy.read", "security.token.read", "audit.identity.read"),
                    action("access.security-session.view", "security.session.read"),
                    action("access.security-session.revoke", "security.session.revoke"),
                    action("access.security-policy.view", "security.policy.read"),
                    action("access.security-policy.manage", "security.policy.manage"),
                    action("access.federation.view", "security.policy.read"),
                    action("access.federation.manage", "security.policy.manage"),
                    action("access.security-token.view", "security.token.read"),
                    action("access.security-token.manage", "security.token.manage"),
                    action("access.audit.view", "audit.identity.read"),
                    action("access.mfa.reset", "security.mfa.reset")),
            feature("resource-access", null, "ADMINISTRATION", 140, "/resource-access", "Resource Access",
                    "Govern ownership, grants, denies, reviews and decision explanations.", TENANT, false,
                    any("resource.governance.read")),
            feature("instance-administration", null, "PLATFORM", 210, "/instance-administration", "Platform Administration",
                    "Manage delegated Instance and Tenant lifecycle controls. Root-only bootstrap and break-glass remain separate.", PLATFORM, true,
                    any("instance.tenant.read", "instance.tenant.manage", "identity.platform_user.read", "identity.platform_role.read"),
                    action("platform.tenants.manage", "instance.tenant.manage"),
                    action("platform.users.manage", "identity.platform_user.create", "identity.platform_user.update", "identity.platform_user.security"),
                    action("platform.roles.manage", "identity.platform_role.manage", "identity.role_permission.manage", "identity.role_binding.manage")),

            // Engineering/governance surfaces remain addressable for qualified root users,
            // but are intentionally excluded from normal product/Admin navigation.
            feature("engineering-tools", null, "INTERNAL", 900, "/cluster", "Engineering Tools",
                    "Open low-level runtime diagnostics and compatibility tools without exposing them in normal administration navigation.", INTERNAL, false,
                    any("permission.entry_point.read")),
            feature("permission-catalog", null, "INTERNAL", 910, "/platform-administration/permission-catalog", "Authorization Registry",
                    "Inspect and publish system-managed Atomic Permission catalog revisions.", INTERNAL, false,
                    any("permission.catalog.read")),
            feature("enforcement-activation", null, "INTERNAL", 920, "/platform-administration/enforcement-activation", "Authorization Rollout",
                    "Operate authorization cutover and last-known-good runtime evidence.", INTERNAL, false,
                    any("permission.enforcement_cutover.read")),
            feature("permission-readiness", null, "INTERNAL", 930, "/platform-administration/permission-readiness", "Authorization Readiness",
                    "Inspect permission manifest coverage and migration diagnostics.", INTERNAL, false,
                    any("permission.entry_point.read"))
    );

    public UiEntitlementResponse project(IamUiSessionResponse session) {
        boolean root = session.roles().contains("INSTANCE_ROOT") || session.roles().contains("ROOT");
        return projectAuthority(
                root,
                root ? "INSTANCE_ROOT" : "TENANT",
                root ? "" : session.selectedTenantId(),
                session.permissions(),
                session.permissionScopes(),
                true);
    }

    /**
     * Uses the same feature/action registry as a real browser Session to preview one Tenant
     * Responsibility. This is a read-only projection; it never creates a Role-to-Menu ACL.
     */
    public UiEntitlementResponse previewTenant(
            String tenantId, Set<String> permissions, Map<String, Set<String>> permissionScopes) {
        return projectAuthority(false, "TENANT", tenantId, permissions, permissionScopes, false);
    }

    /**
     * Adds human-auditable RBAC sources to the same UI projection used by a signed-in Person.
     */
    public EffectiveUiAccessResponse explainEffectiveAccess(
            String tenantId, IamEffectiveAuthorityResponse authority) {
        UiEntitlementResponse ui = previewTenant(tenantId, authority.permissionCodes(), authority.permissionScopes());
        Map<String, List<UiAccessGrantReasonResponse>> reasonsByPermission = new LinkedHashMap<>();
        for (EffectivePermissionResponse permission : authority.effectiveAccess().permissions()) {
            List<UiAccessGrantReasonResponse> reasons = permission.sources().stream()
                    .map(source -> reason(permission.permissionCode(), source))
                    .toList();
            reasonsByPermission.put(permission.permissionCode(), reasons);
        }
        Map<String, List<UiAccessGrantReasonResponse>> pageReasons = new LinkedHashMap<>();
        Map<String, List<UiAccessGrantReasonResponse>> actionReasons = new LinkedHashMap<>();
        for (Feature feature : FEATURES) {
            UiEntitlementResponse.PageEntitlement page = ui.pages().get(feature.id());
            if (page != null && !HIDDEN.equals(page.displayMode())) {
                pageReasons.put(feature.id(), reasons(feature.requiredAny(), reasonsByPermission));
            }
            for (Action action : feature.actions()) {
                UiEntitlementResponse.ActionEntitlement entitlement = ui.actionEntitlements().get(action.id());
                if (entitlement != null && ENABLED.equals(entitlement.displayMode())) {
                    actionReasons.put(action.id(), reasons(action.requiredAny(), reasonsByPermission));
                }
            }
        }
        return new EffectiveUiAccessResponse(tenantId, authority.userId(), authority.evaluatedAt(), ui, pageReasons, actionReasons);
    }

    private UiEntitlementResponse projectAuthority(
            boolean root, String workspaceKind, String tenantId, Set<String> permissions,
            Map<String, Set<String>> permissionScopes, boolean includeSelfService) {
        Set<String> effectivePermissions = permissions == null ? Set.of() : permissions;
        Map<String, Set<String>> effectiveScopes = permissionScopes == null ? Map.of() : permissionScopes;
        Map<String, UiEntitlementResponse.PageEntitlement> pages = new LinkedHashMap<>();
        Set<String> actions = new LinkedHashSet<>();
        Map<String, UiEntitlementResponse.ActionEntitlement> actionEntitlements = new LinkedHashMap<>();
        Map<String, Set<String>> actionScopes = new LinkedHashMap<>();

        for (Feature feature : FEATURES) {
            boolean pageAllowed = allowed(feature, root, effectivePermissions, includeSelfService);
            List<Action> enabledActions = new ArrayList<>();
            if (pageAllowed) {
                for (Action action : feature.actions()) {
                    if (hasAny(effectivePermissions, action.requiredAny(), root)) enabledActions.add(action);
                }
            }
            String displayMode = !pageAllowed ? HIDDEN : enabledActions.isEmpty() ? READ_ONLY : ENABLED;
            String denial = pageAllowed ? "" : denial(feature, root);
            pages.put(feature.id(), new UiEntitlementResponse.PageEntitlement(
                    feature.id(), feature.route(), displayMode, pageAllowed, denial, feature.surface()));

            for (Action action : feature.actions()) {
                boolean actionEnabled = pageAllowed && enabledActions.contains(action);
                Set<String> scopes = actionEnabled
                        ? effectiveActionScopes(action, effectivePermissions, effectiveScopes, root)
                        : Set.of();
                actionEntitlements.put(action.id(), new UiEntitlementResponse.ActionEntitlement(
                        action.id(), actionEnabled ? ENABLED : HIDDEN, scopes));
                if (actionEnabled) {
                    actions.add(action.id());
                    actionScopes.put(action.id(), scopes);
                }
            }
        }

        List<UiEntitlementResponse.NavigationItem> navigation = navigationTree(pages);
        return new UiEntitlementResponse(
                "3.0", workspaceKind, tenantId, navigation, pages, actions, actionEntitlements, actionScopes, Instant.now());
    }

    private static List<UiEntitlementResponse.NavigationItem> navigationTree(
            Map<String, UiEntitlementResponse.PageEntitlement> pages) {
        Map<String, List<Feature>> childrenByParent = new LinkedHashMap<>();
        for (Feature feature : FEATURES) {
            if (feature.parentFeatureId() != null) {
                childrenByParent.computeIfAbsent(feature.parentFeatureId(), ignored -> new ArrayList<>()).add(feature);
            }
        }

        List<UiEntitlementResponse.NavigationItem> roots = new ArrayList<>();
        for (Feature feature : FEATURES) {
            if (feature.parentFeatureId() != null || !feature.navigationVisible()) continue;
            UiEntitlementResponse.PageEntitlement page = pages.get(feature.id());
            if (page == null || HIDDEN.equals(page.displayMode())) continue;

            List<UiEntitlementResponse.NavigationItem> children = new ArrayList<>();
            for (Feature child : childrenByParent.getOrDefault(feature.id(), List.of())) {
                if (!child.navigationVisible()) continue;
                UiEntitlementResponse.PageEntitlement childPage = pages.get(child.id());
                if (childPage == null || HIDDEN.equals(childPage.displayMode())) continue;
                children.add(navigationItem(child, childPage.displayMode(), List.of()));
            }
            children.sort(Comparator.comparingInt(UiEntitlementResponse.NavigationItem::order));
            String rootMode = children.stream().anyMatch(item -> ENABLED.equals(item.displayMode()))
                    ? ENABLED : page.displayMode();
            roots.add(navigationItem(feature, rootMode, children));
        }
        roots.sort(Comparator.comparingInt(UiEntitlementResponse.NavigationItem::order));
        return List.copyOf(roots);
    }

    private static UiEntitlementResponse.NavigationItem navigationItem(
            Feature feature, String displayMode, List<UiEntitlementResponse.NavigationItem> children) {
        return new UiEntitlementResponse.NavigationItem(
                feature.id(), feature.parentFeatureId(), feature.section(), feature.order(), feature.route(),
                feature.label(), feature.purpose(), displayMode, children);
    }

    private static boolean allowed(Feature feature, boolean root, Set<String> permissions, boolean includeSelfService) {
        if (feature.selfService()) return includeSelfService;
        if (INTERNAL.equals(feature.surface())) {
            return root && hasAny(permissions, feature.requiredAny(), true);
        }
        if (PLATFORM.equals(feature.surface())) {
            return root || hasAny(permissions, feature.requiredAny(), false);
        }
        if (root) return true;
        return hasAny(permissions, feature.requiredAny(), false);
    }

    private static String denial(Feature feature, boolean root) {
        if (INTERNAL.equals(feature.surface()) && !root) {
            return "This engineering feature is available only to Instance Root.";
        }
        if (PLATFORM.equals(feature.surface())) {
            return "Your effective Instance permissions do not include Platform Administration.";
        }
        return "Your effective access does not include this feature for the active Tenant.";
    }

    private static boolean hasAny(Set<String> permissions, Set<String> requiredAny, boolean root) {
        if (root) return true;
        if (permissions.contains("*")) return true;
        for (String permission : requiredAny) if (permissions.contains(permission)) return true;
        return false;
    }

    private static Set<String> effectiveActionScopes(
            Action action, Set<String> permissions, Map<String, Set<String>> permissionScopes, boolean root) {
        if (root || permissions.contains("*")) return Set.of("*");
        Set<String> scopes = new LinkedHashSet<>();
        for (String permission : action.requiredAny()) {
            if (!permissions.contains(permission)) continue;
            scopes.addAll(permissionScopes.getOrDefault(permission, Set.of()));
        }
        return Set.copyOf(scopes);
    }

    private static UiAccessGrantReasonResponse reason(
            String permissionCode, EffectiveAccessSourceResponse source) {
        return new UiAccessGrantReasonResponse(
                permissionCode, source.bindingId(), source.roleId(), source.roleName(), source.principalType(),
                source.principalId(), source.scopeType(), source.scopeId(), source.effectiveAt(), source.expiresAt(),
                source.inheritedFromGroup());
    }

    private static List<UiAccessGrantReasonResponse> reasons(
            Set<String> requiredPermissions, Map<String, List<UiAccessGrantReasonResponse>> reasonsByPermission) {
        Map<String, UiAccessGrantReasonResponse> unique = new LinkedHashMap<>();
        for (String permission : requiredPermissions) {
            for (UiAccessGrantReasonResponse reason : reasonsByPermission.getOrDefault(permission, List.of())) {
                unique.putIfAbsent(reason.permissionCode() + ":" + reason.bindingId(), reason);
            }
        }
        return List.copyOf(unique.values());
    }

    private static Set<String> any(String... values) { return Set.of(values); }
    private static Action action(String id, String... requiredAny) { return new Action(id, Set.of(requiredAny)); }

    private static Feature feature(
            String id, String parentFeatureId, String section, int order, String route, String label, String purpose,
            String surface, boolean navigationVisible, Set<String> requiredAny, Action... actions) {
        return new Feature(id, parentFeatureId, section, order, route, label, purpose, surface, navigationVisible,
                false, requiredAny, List.of(actions));
    }

    private static Feature selfServiceFeature(
            String id, String parentFeatureId, String section, int order, String route, String label, String purpose) {
        return new Feature(id, parentFeatureId, section, order, route, label, purpose, TENANT, true, true, Set.of(), List.of());
    }

    private record Feature(
            String id, String parentFeatureId, String section, int order, String route, String label, String purpose,
            String surface, boolean navigationVisible, boolean selfService, Set<String> requiredAny, List<Action> actions) {}
    private record Action(String id, Set<String> requiredAny) {}
}
