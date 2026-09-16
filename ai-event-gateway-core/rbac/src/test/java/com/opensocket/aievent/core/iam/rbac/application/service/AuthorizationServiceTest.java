package com.opensocket.aievent.core.iam.rbac.application.service;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");
    private static final PrincipalRef USER = new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-1");
    private static final Permission PERMISSION = new Permission(new PermissionCode("identity.user.read"),
            "USER", "READ", "Read users", Permission.RiskLevel.LOW, Set.of(ScopeType.TENANT),
            PermissionStatus.ACTIVE, true, 1);
    private static final Role ROLE = Role.reconstitute(new RoleId("identity-viewer"), Optional.empty(),
            new RoleCode("IDENTITY_VIEWER"), "Identity Viewer", "", RoleType.TENANT_ROLE,
            RoleStatus.ACTIVE, true, NOW, NOW, "system", "system", 1);
    private static final PrincipalRoleBinding BINDING = PrincipalRoleBinding.create("binding-1", USER,
            ROLE.roleId(), ScopeRef.tenant("tenant-a"), NOW, null, "admin", NOW);
    @Test void allowsEffectiveTenantGrant() {
        var audit = new CapturingAudit();
        AuthorizationDecision decision = service(new SecurityEpoch(1, 2, 3), audit).authorize(request("tenant-a",
                new SecurityEpoch(1, 2, 3)));
        assertEquals(AuthorizationDecision.Effect.ALLOW, decision.effect());
        assertEquals(Set.of("binding-1"), decision.matchedBindingIds());
        assertEquals("tenant-a", decision.effectiveScopeId());
        assertEquals(1, audit.count);
    }

    @Test void staleEpochFailsClosed() {
        AuthorizationDecision decision = service(new SecurityEpoch(2, 2, 3), new CapturingAudit())
                .authorize(request("tenant-a", new SecurityEpoch(1, 2, 3)));
        assertEquals(AuthorizationDecision.Effect.DENY, decision.effect());
        assertEquals(RbacReasonCode.AUTH_POLICY_VERSION_STALE.name(), decision.reasonCode());
    }


    @Test void instanceRootReceivesOnlyCatalogBackedInstancePermissionWithoutRoleBinding() {
        Permission platformPermission = new Permission(new PermissionCode("permission.catalog.read"),
                "PERMISSION_CATALOG", "READ", "Read permission catalog", Permission.RiskLevel.HIGH,
                Set.of(ScopeType.INSTANCE), PermissionStatus.ACTIVE, true, 1);
        PrincipalRef root = new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root-1");
        AuthorizationRequest request = new AuthorizationRequest(root, TenantRef.instance(),
                "permission.catalog.read", "PERMISSION_CATALOG", "catalog", "INSTANCE", "INSTANCE",
                new SecurityEpoch(1, 0, 3), Map.of());

        AuthorizationDecision decision = service(platformPermission, new SecurityEpoch(1, 0, 3),
                new CapturingAudit()).authorize(request);

        assertEquals(AuthorizationDecision.Effect.ALLOW, decision.effect());
        assertTrue(decision.matchedBindingIds().isEmpty());
        assertTrue(decision.matchedRoleIds().isEmpty());
        assertEquals("INSTANCE", decision.effectiveScopeId());
    }

    @Test void instanceRootCannotUseTenantScopedPermission() {
        PrincipalRef root = new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root-1");
        AuthorizationRequest request = new AuthorizationRequest(root, TenantRef.instance(),
                "identity.user.read", "USER", "user-2", "TENANT", "tenant-a",
                new SecurityEpoch(1, 0, 3), Map.of());

        AuthorizationDecision decision = service(new SecurityEpoch(1, 0, 3),
                new CapturingAudit()).authorize(request);

        assertEquals(AuthorizationDecision.Effect.DENY, decision.effect());
        assertEquals(RbacReasonCode.AUTH_TENANT_MISMATCH.name(), decision.reasonCode());
    }


    @Test void instanceRootCanAdministerSelectedTenantWithCatalogBackedPermission() {
        PrincipalRef root = new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root-1");
        AuthorizationRequest request = new AuthorizationRequest(root, TenantRef.tenant("tenant-a"),
                "identity.user.read", "USER", "user-2", "TENANT", "tenant-a",
                new SecurityEpoch(1, 0, 3), Map.of());

        AuthorizationDecision decision = service(new SecurityEpoch(1, 27, 3),
                new CapturingAudit()).authorize(request);

        assertEquals(AuthorizationDecision.Effect.ALLOW, decision.effect());
        assertTrue(decision.matchedBindingIds().isEmpty());
        assertEquals("tenant-a", decision.effectiveScopeId());
        assertEquals(new SecurityEpoch(1, 27, 3), decision.evaluatedEpoch());
    }

    @Test void instanceRootProjectedTenantStillRejectsStaleGlobalEpoch() {
        PrincipalRef root = new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root-1");
        AuthorizationRequest request = new AuthorizationRequest(root, TenantRef.tenant("tenant-a"),
                "identity.user.read", "USER", "user-2", "TENANT", "tenant-a",
                new SecurityEpoch(1, 0, 3), Map.of());

        AuthorizationDecision decision = service(new SecurityEpoch(2, 27, 3),
                new CapturingAudit()).authorize(request);

        assertEquals(AuthorizationDecision.Effect.DENY, decision.effect());
        assertEquals(RbacReasonCode.AUTH_POLICY_VERSION_STALE.name(), decision.reasonCode());
    }

    @Test void instanceRootProjectedTenantStillRejectsStalePrincipalEpoch() {
        PrincipalRef root = new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root-1");
        AuthorizationRequest request = new AuthorizationRequest(root, TenantRef.tenant("tenant-a"),
                "identity.user.read", "USER", "user-2", "TENANT", "tenant-a",
                new SecurityEpoch(2, 0, 3), Map.of());

        AuthorizationDecision decision = service(new SecurityEpoch(2, 27, 4),
                new CapturingAudit()).authorize(request);

        assertEquals(AuthorizationDecision.Effect.DENY, decision.effect());
        assertEquals(RbacReasonCode.AUTH_POLICY_VERSION_STALE.name(), decision.reasonCode());
    }

    @Test void groupInheritedBindingAllowsMatchingGroupScope() {
        PrincipalRef group = new PrincipalRef(PrincipalRef.PrincipalType.GROUP, "group-a");
        Permission permission = new Permission(new PermissionCode("identity.group.member.read"),
                "GROUP_MEMBER", "READ", "Read group members", Permission.RiskLevel.LOW,
                Set.of(ScopeType.GROUP), PermissionStatus.ACTIVE, true, 1);
        PrincipalRoleBinding binding = PrincipalRoleBinding.create("binding-group", group,
                ROLE.roleId(), ScopeRef.group("tenant-a", "group-a"), NOW, null, "admin", NOW);
        AuthorizationRequest request = new AuthorizationRequest(USER, TenantRef.tenant("tenant-a"),
                permission.code().value(), "GROUP_MEMBER", "user-2", "GROUP", "group-a",
                new SecurityEpoch(1, 2, 3), Map.of());

        AuthorizationDecision decision = service(permission, new SecurityEpoch(1, 2, 3),
                new CapturingAudit(), binding, Set.of(group),
                new OrganizationScopeSnapshot("tenant-a", Set.of(), Set.of("group-a"))).authorize(request);

        assertEquals(AuthorizationDecision.Effect.ALLOW, decision.effect());
        assertEquals(Set.of("binding-group"), decision.matchedBindingIds());
    }

    @Test void directUserBindingAllowsMatchingDepartmentScope() {
        Permission permission = new Permission(new PermissionCode("identity.department.member.read"),
                "DEPARTMENT_MEMBER", "READ", "Read department members", Permission.RiskLevel.LOW,
                Set.of(ScopeType.DEPARTMENT), PermissionStatus.ACTIVE, true, 1);
        PrincipalRoleBinding binding = PrincipalRoleBinding.create("binding-department", USER,
                ROLE.roleId(), ScopeRef.department("tenant-a", "department-a"), NOW, null, "admin", NOW);
        AuthorizationRequest request = new AuthorizationRequest(USER, TenantRef.tenant("tenant-a"),
                permission.code().value(), "DEPARTMENT_MEMBER", "user-2", "DEPARTMENT", "department-a",
                new SecurityEpoch(1, 2, 3), Map.of());

        AuthorizationDecision decision = service(permission, new SecurityEpoch(1, 2, 3),
                new CapturingAudit(), binding, Set.of(),
                new OrganizationScopeSnapshot("tenant-a", Set.of("department-a"), Set.of())).authorize(request);

        assertEquals(AuthorizationDecision.Effect.ALLOW, decision.effect());
        assertEquals(Set.of("binding-department"), decision.matchedBindingIds());
    }

    @Test void departmentSubtreeBindingAllowsDescendantDepartmentButNotSibling() {
        Permission permission = new Permission(new PermissionCode("identity.department.member.read"),
                "DEPARTMENT_MEMBER", "READ", "Read department members", Permission.RiskLevel.LOW,
                Set.of(ScopeType.DEPARTMENT_SUBTREE), PermissionStatus.ACTIVE, true, 1);
        PrincipalRoleBinding binding = PrincipalRoleBinding.create("binding-department-subtree", USER,
                ROLE.roleId(), ScopeRef.departmentSubtree("tenant-a", "department-parent"), NOW, null, "admin", NOW);
        AuthorizationRequest child = new AuthorizationRequest(USER, TenantRef.tenant("tenant-a"),
                permission.code().value(), "DEPARTMENT_MEMBER", "user-2", "DEPARTMENT", "department-child",
                new SecurityEpoch(1, 2, 3), Map.of());
        AuthorizationRequest sibling = new AuthorizationRequest(USER, TenantRef.tenant("tenant-a"),
                permission.code().value(), "DEPARTMENT_MEMBER", "user-3", "DEPARTMENT", "department-sibling",
                new SecurityEpoch(1, 2, 3), Map.of());

        AuthorizationService service = service(permission, new SecurityEpoch(1, 2, 3),
                new CapturingAudit(), binding, Set.of(),
                new OrganizationScopeSnapshot("tenant-a", Set.of("department-child"), Set.of()));

        assertEquals(AuthorizationDecision.Effect.ALLOW, service.authorize(child).effect());
        assertEquals(AuthorizationDecision.Effect.DENY, service.authorize(sibling).effect());
    }

    @Test void spoofedTenantScopeIdFailsClosed() {
        AuthorizationRequest request = new AuthorizationRequest(USER, TenantRef.tenant("tenant-a"),
                "identity.user.read", "USER", "user-2", "TENANT", "tenant-b",
                new SecurityEpoch(1, 2, 3), Map.of());
        AuthorizationDecision decision = service(new SecurityEpoch(1, 2, 3), new CapturingAudit()).authorize(request);
        assertEquals(AuthorizationDecision.Effect.DENY, decision.effect());
        assertEquals(RbacReasonCode.AUTH_TENANT_MISMATCH.name(), decision.reasonCode());
    }

    private AuthorizationRequest request(String scopeId, SecurityEpoch epoch) {
        return new AuthorizationRequest(USER, TenantRef.tenant("tenant-a"), "identity.user.read",
                "USER", "user-2", "TENANT", scopeId, epoch, Map.of());
    }

    private AuthorizationService service(SecurityEpoch authority, CapturingAudit audit) {
        return service(PERMISSION, authority, audit);
    }

    private AuthorizationService service(Permission availablePermission, SecurityEpoch authority,
                                         CapturingAudit audit) {
        return service(availablePermission, authority, audit, BINDING, Set.of(),
                new OrganizationScopeSnapshot("tenant-a", Set.of(), Set.of()));
    }

    private AuthorizationService service(Permission availablePermission, SecurityEpoch authority,
                                         CapturingAudit audit, PrincipalRoleBinding effectiveBinding,
                                         Set<PrincipalRef> expandedPrincipals,
                                         OrganizationScopeSnapshot scopeSnapshot) {
        PermissionCatalogRepository permissionRepo = new PermissionCatalogRepository() {
            public Optional<Permission> findByCode(PermissionCode code) {
                return availablePermission.code().equals(code) ? Optional.of(availablePermission) : Optional.empty();
            }
            public List<Permission> findByCodes(Set<PermissionCode> codes) {
                return codes.contains(availablePermission.code()) ? List.of(availablePermission) : List.of();
            }
            public Set<String> findActiveCodes() { return Set.of(availablePermission.code().value()); }
        };
        PrincipalRoleBindingRepository bindingRepo = new PrincipalRoleBindingRepository() {
            public Optional<PrincipalRoleBinding> findById(String tenantId, String id) { return Optional.of(effectiveBinding); }
            public List<PrincipalRoleBinding> findEffective(String tenantId, Set<PrincipalRef> principals, Instant at) {
                return principals.contains(effectiveBinding.principal()) ? List.of(effectiveBinding) : List.of();
            }
            public PrincipalRoleBinding save(PrincipalRoleBinding binding, long expectedVersion) { return binding; }
        };
        RoleRepository roleRepo = new RoleRepository() {
            public Optional<Role> findById(String tenantId, RoleId id) { return Optional.of(ROLE); }
            public Optional<Role> findByCode(String tenantId, RoleCode code) { return Optional.of(ROLE); }
            public List<Role> findByIds(String tenantId, Set<RoleId> ids) { return List.of(ROLE); }
            public Role save(Role role, long expectedVersion) { return role; }
        };
        RolePermissionRepository grantRepo = new RolePermissionRepository() {
            public List<RolePermissionGrant> findByRoleIds(String tenantId, Set<RoleId> ids) {
                return List.of(new RolePermissionGrant("grant-effective", Optional.empty(),
                        ROLE.roleId(), availablePermission.code(), NOW, "system", 1));
            }
            public void replace(String tenantId, RoleId roleId, List<RolePermissionGrant> grants) { }
        };
        AuthorizationGrantCachePort cache = new AuthorizationGrantCachePort() {
            public Optional<List<ResolvedRoleGrant>> get(String t, PrincipalRef p, long v) { return Optional.empty(); }
            public void put(String t, PrincipalRef p, long v, List<ResolvedRoleGrant> g) { }
            public void evict(String t, String p) { } public void evictTenant(String t) { } public void evictAll() { }
        };
        OrganizationScopePort organizationScope = new OrganizationScopePort() {
            @Override public OrganizationScopeSnapshot resolve(String tenant, PrincipalRef principal) { return scopeSnapshot; }
            @Override public boolean departmentContains(String tenant, String ancestor, String candidate) {
                return Objects.equals(ancestor, candidate)
                        || ("department-parent".equals(ancestor) && "department-child".equals(candidate));
            }
        };
        return new AuthorizationService(permissionRepo, (tenant, principal) -> expandedPrincipals,
                organizationScope,
                bindingRepo, roleRepo, grantRepo,
                tenant -> new PolicyVersion(ScopeType.TENANT, tenant, tenant, 7, NOW, "system"),
                (tenant, principal) -> authority, audit, cache, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class CapturingAudit implements AuthorizationDecisionAuditPort {
        int count;
        public void append(AuthorizationRequest request, AuthorizationDecision decision, long policyVersion) { count++; }
    }
}
