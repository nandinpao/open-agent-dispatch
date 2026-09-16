package com.opensocket.aievent.core.iam.api.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class IamEffectiveAccessTenantTransactionTest {
    @Test
    void opensTenantExecutionBeforePrincipalAndGroupExpansion() {
        RecordingExecution execution = new RecordingExecution();
        PrincipalExpansionPort expansion = (tenantId, principal) -> {
            assertTrue(execution.active);
            assertEquals("tenant-a", execution.tenantId);
            return Set.of(principal);
        };
        PermissionCatalogRepository permissionCatalog = emptyPermissionCatalog();
        OrganizationScopePort organizationScope = (tenantId, principal) ->
                new OrganizationScopeSnapshot(tenantId, Set.of(), Set.of());
        PrincipalRoleBindingRepository bindings = new PrincipalRoleBindingRepository() {
            @Override public Optional<PrincipalRoleBinding> findById(String tenantId, String bindingId) { return Optional.empty(); }
            @Override public List<PrincipalRoleBinding> findEffective(String tenantId, Set<PrincipalRef> principals, Instant at) {
                assertTrue(execution.active);
                return List.of();
            }
            @Override public PrincipalRoleBinding save(PrincipalRoleBinding binding, long expectedVersion) { return binding; }
        };
        RoleRepository roles = new RoleRepository() {
            @Override public Optional<Role> findById(String tenantId, RoleId roleId) { return Optional.empty(); }
            @Override public Optional<Role> findByCode(String tenantId, RoleCode code) { return Optional.empty(); }
            @Override public List<Role> findByIds(String tenantId, Set<RoleId> roleIds) {
                assertTrue(execution.active);
                return List.of();
            }
            @Override public Role save(Role role, long expectedVersion) { return role; }
        };
        RolePermissionRepository rolePermissions = new RolePermissionRepository() {
            @Override public List<RolePermissionGrant> findByRoleIds(String tenantId, Set<RoleId> roleIds) {
                assertTrue(execution.active);
                return List.of();
            }
            @Override public void replace(String tenantId, RoleId roleId, List<RolePermissionGrant> grants) { }
        };
        IamEffectiveAccessQueryService service = new IamEffectiveAccessQueryService(
                expansion,
                permissionCatalog,
                organizationScope,
                bindings,
                roles,
                rolePermissions,
                Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC),
                execution);

        var response = service.effectiveAccess("tenant-a", "user-a");

        assertEquals("tenant-a", response.tenantId());
        assertEquals("user-a", response.userId());
        assertEquals(List.of(), response.permissions());
        assertEquals(1, execution.readCount);
        assertTrue(!execution.active);
    }

    @Test
    void canonicalAuthorityIncludesGroupInheritedRolesAndPermissions() {
        RecordingExecution execution = new RecordingExecution();
        Instant at = Instant.parse("2026-08-07T00:00:00Z");
        RoleId roleId = new RoleId("role-group-operator");
        Role role = Role.reconstitute(
                roleId, Optional.empty(), new RoleCode("GROUP_OPERATOR"), "Group Operator",
                "Inherited group role", RoleType.SYSTEM_ROLE, RoleStatus.ACTIVE, true,
                at, at, "system", "system", 1);
        PrincipalRef user = new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a");
        PrincipalRef group = new PrincipalRef(PrincipalRef.PrincipalType.GROUP, "group-a");
        PrincipalRoleBinding binding = PrincipalRoleBinding.create(
                "binding-group", group, roleId, ScopeRef.tenant("tenant-a"), at, null, "admin", at);
        RolePermissionGrant grant = new RolePermissionGrant(
                "grant-task-read", Optional.empty(), roleId, new PermissionCode("task.read"), at, "system", 1);
        Permission permission = new Permission(
                new PermissionCode("task.read"), "TASK", "READ", "Read tasks", Permission.RiskLevel.LOW,
                Set.of(ScopeType.TENANT), PermissionStatus.ACTIVE, true, 1);
        PermissionCatalogRepository permissionCatalog = new PermissionCatalogRepository() {
            @Override public Optional<Permission> findByCode(PermissionCode code) { return Optional.of(permission); }
            @Override public List<Permission> findByCodes(Set<PermissionCode> codes) { return List.of(permission); }
            @Override public Set<String> findActiveCodes() { return Set.of("task.read"); }
        };
        OrganizationScopePort organizationScope = (tenantId, principal) ->
                new OrganizationScopeSnapshot(tenantId, Set.of(), Set.of("group-a"));

        PrincipalExpansionPort expansion = (tenantId, principal) -> Set.of(user, group);
        PrincipalRoleBindingRepository bindings = new PrincipalRoleBindingRepository() {
            @Override public Optional<PrincipalRoleBinding> findById(String tenantId, String bindingId) { return Optional.of(binding); }
            @Override public List<PrincipalRoleBinding> findEffective(String tenantId, Set<PrincipalRef> principals, Instant instant) {
                assertTrue(principals.contains(group));
                return List.of(binding);
            }
            @Override public PrincipalRoleBinding save(PrincipalRoleBinding value, long expectedVersion) { return value; }
        };
        RoleRepository roles = new RoleRepository() {
            @Override public Optional<Role> findById(String tenantId, RoleId id) { return Optional.of(role); }
            @Override public Optional<Role> findByCode(String tenantId, RoleCode code) { return Optional.of(role); }
            @Override public List<Role> findByIds(String tenantId, Set<RoleId> ids) { return List.of(role); }
            @Override public Role save(Role value, long expectedVersion) { return value; }
        };
        RolePermissionRepository permissions = new RolePermissionRepository() {
            @Override public List<RolePermissionGrant> findByRoleIds(String tenantId, Set<RoleId> ids) { return List.of(grant); }
            @Override public void replace(String tenantId, RoleId id, List<RolePermissionGrant> grants) { }
        };
        IamEffectiveAccessQueryService service = new IamEffectiveAccessQueryService(
                expansion, permissionCatalog, organizationScope, bindings, roles, permissions,
                Clock.fixed(at, ZoneOffset.UTC), execution);

        var authority = service.effectiveAuthority("tenant-a", "user-a");

        assertEquals(Set.of("GROUP_OPERATOR"), authority.roleCodes());
        assertEquals(Set.of("task.read"), authority.permissionCodes());
        assertEquals(Set.of("TENANT:tenant-a"), authority.permissionScopes().get("task.read"));
        assertEquals(1, authority.effectiveAccess().permissions().size());
        assertEquals("GROUP", authority.effectiveAccess().permissions().getFirst().sources().getFirst().principalType());
        assertTrue(authority.effectiveAccess().permissions().getFirst().sources().getFirst().inheritedFromGroup());
        assertEquals(1, execution.readCount);
    }

    @Test
    void canonicalAuthorityProjectsDepartmentScopedPermission() {
        RecordingExecution execution = new RecordingExecution();
        Instant at = Instant.parse("2026-08-07T00:00:00Z");
        RoleId roleId = new RoleId("role-department-reader");
        Role role = Role.reconstitute(
                roleId, Optional.of("tenant-a"), new RoleCode("DEPARTMENT_READER"), "Department Reader",
                "Department-scoped read role", RoleType.CUSTOM_TENANT_ROLE, RoleStatus.ACTIVE, false,
                at, at, "admin", "admin", 1);
        PrincipalRef user = new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a");
        PrincipalRoleBinding binding = PrincipalRoleBinding.create(
                "binding-department", user, roleId, ScopeRef.department("tenant-a", "dept-it"), at, null, "admin", at);
        RolePermissionGrant grant = new RolePermissionGrant(
                "grant-user-read", Optional.empty(), roleId, new PermissionCode("identity.user.read"), at, "admin", 1);
        Permission permission = new Permission(
                new PermissionCode("identity.user.read"), "USER", "READ", "Read users", Permission.RiskLevel.MEDIUM,
                Set.of(ScopeType.TENANT, ScopeType.DEPARTMENT, ScopeType.GROUP), PermissionStatus.ACTIVE, true, 1);
        IamEffectiveAccessQueryService service = service(
                execution,
                Set.of(user),
                new OrganizationScopeSnapshot("tenant-a", Set.of("dept-it"), Set.of()),
                binding,
                role,
                grant,
                permission,
                at);

        var authority = service.effectiveAuthority("tenant-a", "user-a");

        assertEquals(Set.of("DEPARTMENT_READER"), authority.roleCodes());
        assertEquals(Set.of("identity.user.read"), authority.permissionCodes());
        assertEquals(Set.of("DEPARTMENT:dept-it"), authority.permissionScopes().get("identity.user.read"));
        assertEquals("DEPARTMENT", authority.effectiveAccess().permissions().getFirst().sources().getFirst().scopeType());
    }

    @Test
    void canonicalAuthorityExcludesBindingOutsideUsersOrganizationScope() {
        RecordingExecution execution = new RecordingExecution();
        Instant at = Instant.parse("2026-08-07T00:00:00Z");
        RoleId roleId = new RoleId("role-foreign-department-reader");
        Role role = Role.reconstitute(
                roleId, Optional.of("tenant-a"), new RoleCode("FOREIGN_DEPARTMENT_READER"), "Foreign Department Reader",
                "Must not become effective outside the user's organization scope", RoleType.CUSTOM_TENANT_ROLE,
                RoleStatus.ACTIVE, false, at, at, "admin", "admin", 1);
        PrincipalRef user = new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a");
        PrincipalRoleBinding binding = PrincipalRoleBinding.create(
                "binding-foreign-department", user, roleId,
                ScopeRef.department("tenant-a", "dept-finance"), at, null, "admin", at);
        RolePermissionGrant grant = new RolePermissionGrant(
                "grant-user-read-foreign", Optional.empty(), roleId, new PermissionCode("identity.user.read"), at, "admin", 1);
        Permission permission = new Permission(
                new PermissionCode("identity.user.read"), "USER", "READ", "Read users", Permission.RiskLevel.MEDIUM,
                Set.of(ScopeType.TENANT, ScopeType.DEPARTMENT, ScopeType.GROUP), PermissionStatus.ACTIVE, true, 1);
        IamEffectiveAccessQueryService service = service(
                execution,
                Set.of(user),
                new OrganizationScopeSnapshot("tenant-a", Set.of("dept-it"), Set.of()),
                binding,
                role,
                grant,
                permission,
                at);

        var authority = service.effectiveAuthority("tenant-a", "user-a");

        assertTrue(authority.roleCodes().isEmpty());
        assertTrue(authority.permissionCodes().isEmpty());
        assertTrue(authority.permissionScopes().isEmpty());
        assertTrue(authority.effectiveAccess().permissions().isEmpty());
    }

    private static IamEffectiveAccessQueryService service(
            RecordingExecution execution,
            Set<PrincipalRef> expandedPrincipals,
            OrganizationScopeSnapshot scopeSnapshot,
            PrincipalRoleBinding binding,
            Role role,
            RolePermissionGrant grant,
            Permission permission,
            Instant at) {
        PrincipalExpansionPort expansion = (tenantId, principal) -> expandedPrincipals;
        PermissionCatalogRepository permissionCatalog = new PermissionCatalogRepository() {
            @Override public Optional<Permission> findByCode(PermissionCode code) { return Optional.of(permission); }
            @Override public List<Permission> findByCodes(Set<PermissionCode> codes) { return codes.isEmpty() ? List.of() : List.of(permission); }
            @Override public Set<String> findActiveCodes() { return Set.of(permission.code().value()); }
        };
        OrganizationScopePort organizationScope = (tenantId, principal) -> scopeSnapshot;
        PrincipalRoleBindingRepository bindings = new PrincipalRoleBindingRepository() {
            @Override public Optional<PrincipalRoleBinding> findById(String tenantId, String bindingId) { return Optional.of(binding); }
            @Override public List<PrincipalRoleBinding> findEffective(String tenantId, Set<PrincipalRef> principals, Instant instant) {
                return principals.contains(binding.principal()) ? List.of(binding) : List.of();
            }
            @Override public PrincipalRoleBinding save(PrincipalRoleBinding value, long expectedVersion) { return value; }
        };
        RoleRepository roles = new RoleRepository() {
            @Override public Optional<Role> findById(String tenantId, RoleId id) { return Optional.of(role); }
            @Override public Optional<Role> findByCode(String tenantId, RoleCode code) { return Optional.of(role); }
            @Override public List<Role> findByIds(String tenantId, Set<RoleId> ids) { return ids.contains(role.roleId()) ? List.of(role) : List.of(); }
            @Override public Role save(Role value, long expectedVersion) { return value; }
        };
        RolePermissionRepository rolePermissions = new RolePermissionRepository() {
            @Override public List<RolePermissionGrant> findByRoleIds(String tenantId, Set<RoleId> ids) {
                return ids.contains(role.roleId()) ? List.of(grant) : List.of();
            }
            @Override public void replace(String tenantId, RoleId id, List<RolePermissionGrant> grants) { }
        };
        return new IamEffectiveAccessQueryService(
                expansion, permissionCatalog, organizationScope, bindings, roles, rolePermissions,
                Clock.fixed(at, ZoneOffset.UTC), execution);
    }

    private static PermissionCatalogRepository emptyPermissionCatalog() {
        return new PermissionCatalogRepository() {
            @Override public Optional<Permission> findByCode(PermissionCode code) { return Optional.empty(); }
            @Override public List<Permission> findByCodes(Set<PermissionCode> codes) { return List.of(); }
            @Override public Set<String> findActiveCodes() { return Set.of(); }
        };
    }

    private static final class RecordingExecution implements TenantRbacExecutionPort {
        private boolean active;
        private String tenantId;
        private int readCount;

        @Override
        public <T> T read(String tenantId, String actorId, Supplier<T> work) {
            this.active = true;
            this.tenantId = tenantId;
            this.readCount++;
            try {
                return work.get();
            } finally {
                this.active = false;
            }
        }

        @Override
        public <T> T write(String tenantId, String actorId, Supplier<T> work) {
            throw new AssertionError("read service must not request a write transaction");
        }
    }
}
