package com.opensocket.aievent.core.iam.rbac.application.service;

import com.opensocket.aievent.core.iam.rbac.application.port.in.EffectivePermissionScopePort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.EffectivePermissionScopeQuery;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * RS1 authority enumerator. Unlike point authorization it preserves every effective Role Binding
 * scope, which is required to compile complete SQL row-scope plans for users with multiple scoped
 * responsibilities.
 */
public final class EffectivePermissionScopeService implements EffectivePermissionScopePort {
    private final PermissionCatalogRepository permissions;
    private final PrincipalExpansionPort principalExpansion;
    private final PrincipalRoleBindingRepository bindings;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final PolicyVersionRepository policyVersions;
    private final SecurityEpochAuthorityPort epochs;
    private final Clock clock;

    public EffectivePermissionScopeService(
            PermissionCatalogRepository permissions,
            PrincipalExpansionPort principalExpansion,
            PrincipalRoleBindingRepository bindings,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            PolicyVersionRepository policyVersions,
            SecurityEpochAuthorityPort epochs,
            Clock clock) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.principalExpansion = Objects.requireNonNull(principalExpansion, "principalExpansion");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.rolePermissions = Objects.requireNonNull(rolePermissions, "rolePermissions");
        this.policyVersions = Objects.requireNonNull(policyVersions, "policyVersions");
        this.epochs = Objects.requireNonNull(epochs, "epochs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EffectivePermissionScopeSet resolve(EffectivePermissionScopeQuery query) {
        Objects.requireNonNull(query, "query");
        Instant at = clock.instant();
        String tenantId = query.activeTenant().tenantId();
        PermissionCode permissionCode;
        try {
            permissionCode = new PermissionCode(query.permissionCode());
        } catch (RuntimeException malformed) {
            return denied(tenantId, query.permissionCode(), "AUTH_PERMISSION_UNKNOWN", 0, SecurityEpoch.ZERO, at);
        }

        Permission permission = permissions.findByCode(permissionCode).orElse(null);
        if (permission == null) return denied(tenantId, query.permissionCode(), "AUTH_PERMISSION_UNKNOWN", 0, SecurityEpoch.ZERO, at);
        if (!permission.active()) return denied(tenantId, query.permissionCode(), "AUTH_PERMISSION_DISABLED", 0, SecurityEpoch.ZERO, at);

        SecurityEpoch authority = epochs.current(query.activeTenant(), query.principal());
        if (!fresh(query.principal(), query.presentedEpoch(), authority)) {
            return denied(tenantId, query.permissionCode(), "AUTH_POLICY_VERSION_STALE", 0, authority, at);
        }

        long policyVersion = policyVersions.current(tenantId).value();
        if (query.principal().principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT) {
            if (!permission.supports(ScopeType.TENANT)) {
                return denied(tenantId, query.permissionCode(), "AUTH_SCOPE_UNSUPPORTED", policyVersion, authority, at);
            }
            return new EffectivePermissionScopeSet(tenantId, query.permissionCode(), true, "AUTH_PERMISSION_GRANTED",
                    List.of(new EffectivePermissionScopeGrant(ScopeType.TENANT, tenantId, Set.of(), Set.of())),
                    policyVersion, authority, at);
        }

        Set<PrincipalRef> principals = new LinkedHashSet<>(principalExpansion.expand(tenantId, query.principal()));
        principals.add(query.principal());
        List<PrincipalRoleBinding> effectiveBindings = bindings.findEffective(tenantId, principals, at).stream()
                .filter(binding -> binding.effectiveAt(at))
                .toList();
        if (effectiveBindings.isEmpty()) {
            return denied(tenantId, query.permissionCode(), "AUTH_PERMISSION_DENIED", policyVersion, authority, at);
        }

        Set<RoleId> roleIds = new LinkedHashSet<>();
        effectiveBindings.forEach(binding -> roleIds.add(binding.roleId()));
        Map<RoleId, Role> activeRoles = new HashMap<>();
        for (Role role : roles.findByIds(tenantId, roleIds)) {
            if (role.active()) activeRoles.put(role.roleId(), role);
        }
        Set<RoleId> rolesWithPermission = new LinkedHashSet<>();
        for (RolePermissionGrant grant : rolePermissions.findByRoleIds(tenantId, roleIds)) {
            if (grant.permissionCode().equals(permission.code())) rolesWithPermission.add(grant.roleId());
        }

        Map<String, Accumulator> byScope = new LinkedHashMap<>();
        for (PrincipalRoleBinding binding : effectiveBindings) {
            Role role = activeRoles.get(binding.roleId());
            if (role == null || !rolesWithPermission.contains(binding.roleId())) continue;
            if (!permission.supports(binding.scope().type())) continue;
            String key = binding.scope().type().name() + "|" + binding.scope().scopeId();
            Accumulator accumulator = byScope.computeIfAbsent(key,
                    ignored -> new Accumulator(binding.scope().type(), binding.scope().scopeId()));
            accumulator.bindingIds.add(binding.bindingId());
            accumulator.roleIds.add(role.roleId().value());
        }
        if (byScope.isEmpty()) {
            return denied(tenantId, query.permissionCode(), "AUTH_PERMISSION_DENIED", policyVersion, authority, at);
        }

        List<EffectivePermissionScopeGrant> grants = byScope.values().stream()
                .sorted(Comparator.comparing((Accumulator value) -> value.scopeType.name()).thenComparing(value -> value.scopeId))
                .map(value -> new EffectivePermissionScopeGrant(value.scopeType, value.scopeId,
                        Set.copyOf(value.bindingIds), Set.copyOf(value.roleIds)))
                .toList();
        return new EffectivePermissionScopeSet(tenantId, query.permissionCode(), true, "AUTH_PERMISSION_GRANTED",
                grants, policyVersion, authority, at);
    }

    private static boolean fresh(PrincipalRef principal, SecurityEpoch presented, SecurityEpoch authority) {
        if (principal.principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT) {
            return presented.globalEpoch() >= authority.globalEpoch()
                    && presented.principalEpoch() >= authority.principalEpoch();
        }
        return presented.isAtLeast(authority);
    }

    private static EffectivePermissionScopeSet denied(String tenantId, String permissionCode, String reason,
            long policyVersion, SecurityEpoch epoch, Instant at) {
        return new EffectivePermissionScopeSet(tenantId, permissionCode, false, reason, List.of(), policyVersion, epoch, at);
    }

    private static final class Accumulator {
        private final ScopeType scopeType;
        private final String scopeId;
        private final Set<String> bindingIds = new LinkedHashSet<>();
        private final Set<String> roleIds = new LinkedHashSet<>();
        private Accumulator(ScopeType scopeType, String scopeId) {
            this.scopeType = scopeType;
            this.scopeId = scopeId;
        }
    }
}
