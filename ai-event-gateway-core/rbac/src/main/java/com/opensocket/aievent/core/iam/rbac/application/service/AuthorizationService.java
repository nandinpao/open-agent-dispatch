package com.opensocket.aievent.core.iam.rbac.application.service;

import com.opensocket.aievent.core.iam.rbac.application.port.in.AuthorizationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Framework-free allow-only RBAC decision engine. Invalid or stale requests fail closed. */
public final class AuthorizationService implements AuthorizationPort {
    private final PermissionCatalogRepository permissions;
    private final PrincipalExpansionPort principalExpansion;
    private final OrganizationScopePort organizationScope;
    private final PrincipalRoleBindingRepository bindings;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final PolicyVersionRepository policyVersions;
    private final SecurityEpochAuthorityPort epochs;
    private final AuthorizationDecisionAuditPort audit;
    private final AuthorizationGrantCachePort cache;
    private final Clock clock;

    public AuthorizationService(PermissionCatalogRepository permissions,
                                PrincipalExpansionPort principalExpansion,
                                OrganizationScopePort organizationScope,
                                PrincipalRoleBindingRepository bindings,
                                RoleRepository roles,
                                RolePermissionRepository rolePermissions,
                                PolicyVersionRepository policyVersions,
                                SecurityEpochAuthorityPort epochs,
                                AuthorizationDecisionAuditPort audit,
                                AuthorizationGrantCachePort cache,
                                Clock clock) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.principalExpansion = Objects.requireNonNull(principalExpansion, "principalExpansion");
        this.organizationScope = Objects.requireNonNull(organizationScope, "organizationScope");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.rolePermissions = Objects.requireNonNull(rolePermissions, "rolePermissions");
        this.policyVersions = Objects.requireNonNull(policyVersions, "policyVersions");
        this.epochs = Objects.requireNonNull(epochs, "epochs");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        Instant at = clock.instant();
        String tenantId = request.activeTenant().scope() == TenantRef.Scope.TENANT
                ? request.activeTenant().tenantId() : "";

        PermissionCode permissionCode;
        ScopeType requestedScope;
        try {
            permissionCode = new PermissionCode(request.permission());
            requestedScope = parseScope(request, tenantId);
        } catch (RuntimeException malformed) {
            return finish(request, deny(RbacReasonCode.ROLE_BINDING_SCOPE_INVALID, at, SecurityEpoch.ZERO), 0);
        }

        if (!scopeRequestMatchesTenant(request, requestedScope, tenantId)) {
            return finish(request, deny(RbacReasonCode.AUTH_TENANT_MISMATCH, at, SecurityEpoch.ZERO), 0);
        }

        Permission permission = permissions.findByCode(permissionCode).orElse(null);
        if (permission == null) {
            return finish(request, deny(RbacReasonCode.AUTH_PERMISSION_UNKNOWN, at, SecurityEpoch.ZERO), 0);
        }
        if (!permission.active()) {
            return finish(request, deny(RbacReasonCode.AUTH_PERMISSION_DISABLED, at, SecurityEpoch.ZERO), 0);
        }
        if (!permission.supports(requestedScope)) {
            return finish(request, deny(RbacReasonCode.AUTH_SCOPE_UNSUPPORTED, at, SecurityEpoch.ZERO), 0);
        }

        SecurityEpoch authority = epochs.current(request.activeTenant(), request.principal());
        if (!isFreshForRequest(request, authority)) {
            return finish(request, deny(RbacReasonCode.AUTH_POLICY_VERSION_STALE, at, authority), 0);
        }

        /*
         * INSTANCE_ROOT is the installation and break-glass identity and is deliberately excluded from
         * Role Bindings. R3 allows it to administer the currently selected Tenant while preserving all
         * Permission Catalog, lifecycle, scope, Tenant-mismatch and Security Epoch checks above. Root
         * therefore remains one Principal with one Session and one Authorization Decision path; it is
         * not translated into a compatibility role.
         */
        if (request.principal().principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT) {
            AuthorizationDecision decision = new AuthorizationDecision(UUID.randomUUID().toString(),
                    AuthorizationDecision.Effect.ALLOW, RbacReasonCode.AUTH_PERMISSION_GRANTED.name(),
                    Set.of(), Set.of(), requestedScope.name(),
                    normalizedEffectiveScopeId(request, requestedScope, tenantId), authority, at);
            return finish(request, decision, 0);
        }

        PolicyVersion policy = policyVersions.current(tenantId);
        List<ResolvedRoleGrant> grants;
        Optional<List<ResolvedRoleGrant>> cached = cache.get(tenantId, request.principal(), policy.value());
        if (cached.isPresent()) {
            List<ResolvedRoleGrant> fresh = cached.get().stream()
                    .filter(grant -> grant.binding().effectiveAt(at))
                    .toList();
            if (fresh.size() != cached.get().size()) {
                cache.evict(tenantId, request.principal().principalId());
                grants = resolveGrants(tenantId, request.principal(), at, policy.value());
            } else {
                grants = fresh;
            }
        } else {
            grants = resolveGrants(tenantId, request.principal(), at, policy.value());
        }
        OrganizationScopeSnapshot scopeSnapshot = tenantId.isEmpty()
                ? null : organizationScope.resolve(tenantId, request.principal());

        List<ResolvedRoleGrant> matched = grants.stream()
                .filter(grant -> grant.permission().code().equals(permission.code()))
                .filter(grant -> grant.permission().supports(grant.binding().scope().type()))
                .filter(grant -> scopeMatches(grant.binding().scope(), requestedScope,
                        request.requestedScopeId(), tenantId, scopeSnapshot))
                .toList();

        if (matched.isEmpty()) {
            return finish(request, deny(RbacReasonCode.AUTH_PERMISSION_DENIED, at, authority), policy.value());
        }

        Set<String> bindingIds = new LinkedHashSet<>();
        Set<String> roleIds = new LinkedHashSet<>();
        for (ResolvedRoleGrant grant : matched) {
            bindingIds.add(grant.binding().bindingId());
            roleIds.add(grant.role().roleId().value());
        }
        AuthorizationDecision decision = new AuthorizationDecision(UUID.randomUUID().toString(),
                AuthorizationDecision.Effect.ALLOW, RbacReasonCode.AUTH_PERMISSION_GRANTED.name(),
                bindingIds, roleIds, requestedScope.name(), normalizedEffectiveScopeId(request, requestedScope, tenantId),
                authority, at);
        return finish(request, decision, policy.value());
    }

    /**
     * INSTANCE_ROOT owns one INSTANCE-scoped Session. When a Tenant route is explicitly addressed,
     * the request context is projected to that Tenant only for scope validation and persistence.
     * Root never consumes Tenant Role Bindings, so a Tenant policy epoch must not invalidate the
     * INSTANCE Session. Global and Root-principal epochs remain mandatory and fail closed.
     */
    private boolean isFreshForRequest(AuthorizationRequest request, SecurityEpoch authority) {
        SecurityEpoch presented = request.presentedEpoch();
        if (request.principal().principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT) {
            return presented.globalEpoch() >= authority.globalEpoch()
                    && presented.principalEpoch() >= authority.principalEpoch();
        }
        return presented.isAtLeast(authority);
    }

    private List<ResolvedRoleGrant> resolveGrants(String tenantId, PrincipalRef principal,
                                                  Instant at, long policyVersion) {
        Set<PrincipalRef> principals = new LinkedHashSet<>(principalExpansion.expand(tenantId, principal));
        principals.add(principal);
        List<PrincipalRoleBinding> effective = bindings.findEffective(tenantId, principals, at).stream()
                .filter(binding -> binding.effectiveAt(at))
                .toList();

        Set<RoleId> roleIds = new LinkedHashSet<>();
        effective.forEach(binding -> roleIds.add(binding.roleId()));
        Map<RoleId, Role> roleMap = new HashMap<>();
        for (Role role : roles.findByIds(tenantId, roleIds)) {
            if (role.active()) roleMap.put(role.roleId(), role);
        }

        List<RolePermissionGrant> grants = rolePermissions.findByRoleIds(tenantId, roleIds);
        Map<RoleId, List<RolePermissionGrant>> byRole = new HashMap<>();
        for (RolePermissionGrant grant : grants) {
            byRole.computeIfAbsent(grant.roleId(), ignored -> new ArrayList<>()).add(grant);
        }

        Set<PermissionCode> codes = new LinkedHashSet<>();
        grants.forEach(grant -> codes.add(grant.permissionCode()));
        Map<PermissionCode, Permission> permissionMap = new HashMap<>();
        for (Permission permission : permissions.findByCodes(codes)) {
            if (permission.active()) permissionMap.put(permission.code(), permission);
        }

        List<ResolvedRoleGrant> resolved = new ArrayList<>();
        for (PrincipalRoleBinding binding : effective) {
            Role role = roleMap.get(binding.roleId());
            if (role == null) continue;
            for (RolePermissionGrant grant : byRole.getOrDefault(role.roleId(), List.of())) {
                Permission permission = permissionMap.get(grant.permissionCode());
                if (permission != null) resolved.add(new ResolvedRoleGrant(binding, role, permission));
            }
        }
        List<ResolvedRoleGrant> immutable = List.copyOf(resolved);
        cache.put(tenantId, principal, policyVersion, immutable);
        return immutable;
    }

    private boolean scopeMatches(ScopeRef binding, ScopeType requested, String requestedId,
                                 String tenantId, OrganizationScopeSnapshot snapshot) {
        if (binding.type() == ScopeType.INSTANCE) return requested == ScopeType.INSTANCE;
        if (binding.type() == ScopeType.TENANT) {
            return !tenantId.isEmpty() && binding.tenantId().equals(tenantId);
        }
        if (binding.type() == ScopeType.DEPARTMENT) {
            return requested == ScopeType.DEPARTMENT && binding.scopeId().equals(requestedId);
        }
        if (binding.type() == ScopeType.DEPARTMENT_SUBTREE) {
            return requested == ScopeType.DEPARTMENT
                    && requestedId != null && !requestedId.isBlank()
                    && organizationScope.departmentContains(tenantId,binding.scopeId(),requestedId);
        }
        if (binding.type() == ScopeType.GROUP) {
            return requested == ScopeType.GROUP && binding.scopeId().equals(requestedId);
        }
        return false;
    }

    private boolean scopeRequestMatchesTenant(AuthorizationRequest request, ScopeType requestedScope,
                                              String tenantId) {
        if (requestedScope == ScopeType.INSTANCE) {
            return tenantId.isEmpty() && request.activeTenant().scope() == TenantRef.Scope.INSTANCE;
        }
        if (tenantId.isEmpty() || request.activeTenant().scope() != TenantRef.Scope.TENANT) return false;
        if (requestedScope == ScopeType.TENANT) {
            String requestedId = request.requestedScopeId();
            return requestedId == null || requestedId.isBlank() || tenantId.equals(requestedId.trim());
        }
        return request.requestedScopeId() != null && !request.requestedScopeId().isBlank();
    }

    private String normalizedEffectiveScopeId(AuthorizationRequest request, ScopeType requestedScope,
                                              String tenantId) {
        if (requestedScope == ScopeType.INSTANCE) return "INSTANCE";
        if (requestedScope == ScopeType.TENANT) return tenantId;
        return request.requestedScopeId();
    }

    private ScopeType parseScope(AuthorizationRequest request, String tenantId) {
        String raw = request.requestedScopeType();
        if (raw == null || raw.isBlank()) return tenantId.isEmpty() ? ScopeType.INSTANCE : ScopeType.TENANT;
        return ScopeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private AuthorizationDecision deny(RbacReasonCode reason, Instant at, SecurityEpoch epoch) {
        return new AuthorizationDecision(UUID.randomUUID().toString(), AuthorizationDecision.Effect.DENY,
                reason.name(), Set.of(), Set.of(), "", "", epoch, at);
    }

    private AuthorizationDecision finish(AuthorizationRequest request, AuthorizationDecision decision,
                                         long policyVersion) {
        audit.append(request, decision, policyVersion);
        return decision;
    }
}
