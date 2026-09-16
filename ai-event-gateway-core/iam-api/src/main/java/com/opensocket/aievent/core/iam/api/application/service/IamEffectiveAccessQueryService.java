package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.request.PreviewRoleBindingRequest;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Read-only explanation service for the Account Management Access tab. */
public final class IamEffectiveAccessQueryService {
    private final PrincipalExpansionPort expansion;
    private final PermissionCatalogRepository permissions;
    private final OrganizationScopePort organizationScope;
    private final PrincipalRoleBindingRepository bindings;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final Clock clock;
    private final TenantRbacExecutionPort execution;

    public IamEffectiveAccessQueryService(
            PrincipalExpansionPort expansion,
            PermissionCatalogRepository permissions,
            OrganizationScopePort organizationScope,
            PrincipalRoleBindingRepository bindings,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            Clock clock,
            TenantRbacExecutionPort execution) {
        this.expansion = Objects.requireNonNull(expansion);
        this.permissions = Objects.requireNonNull(permissions);
        this.organizationScope = Objects.requireNonNull(organizationScope);
        this.bindings = Objects.requireNonNull(bindings);
        this.roles = Objects.requireNonNull(roles);
        this.rolePermissions = Objects.requireNonNull(rolePermissions);
        this.clock = Objects.requireNonNull(clock);
        this.execution = Objects.requireNonNull(execution);
    }

    public EffectiveAccessResponse effectiveAccess(String tenantId, String userId) {
        return execution.read(
                tenantId,
                "iam-api:effective-access",
                () -> effectiveAuthorityInTransaction(tenantId, userId).effectiveAccess());
    }

    /**
     * Single canonical projection used by browser Session and Effective Access views.
     * It deliberately expands indirect principals (for example Group membership) before
     * resolving active Role Bindings, Roles and Permissions.
     */
    public IamEffectiveAuthorityResponse effectiveAuthority(String tenantId, String userId) {
        return execution.read(
                tenantId,
                "iam-api:effective-authority",
                () -> effectiveAuthorityInTransaction(tenantId, userId));
    }

    public RoleBindingAssignmentPreviewResponse previewAssignment(
            String tenantId,
            PreviewRoleBindingRequest request) {
        return execution.read(
                tenantId,
                "iam-api:role-binding-preview",
                () -> previewAssignmentInTransaction(tenantId, request));
    }

    public RoleBindingRevocationPreviewResponse previewRevocation(
            String tenantId,
            String userId,
            String bindingId) {
        return execution.read(
                tenantId,
                "iam-api:role-binding-revocation-preview",
                () -> previewRevocationInTransaction(tenantId, userId, bindingId));
    }

    private EffectiveAccessResponse effectiveAccessInTransaction(String tenantId, String userId) {
        return effectiveAuthorityInTransaction(tenantId, userId).effectiveAccess();
    }

    private IamEffectiveAuthorityResponse effectiveAuthorityInTransaction(String tenantId, String userId) {
        Instant now = clock.instant();
        PrincipalRef user = new PrincipalRef(PrincipalRef.PrincipalType.USER, userId);
        Set<PrincipalRef> principals = expansion.expand(tenantId, user);
        OrganizationScopeSnapshot scopeSnapshot = organizationScope.resolve(tenantId, user);
        List<PrincipalRoleBinding> effective = bindings.findEffective(tenantId, principals, now).stream()
                .filter(value -> value.effectiveAt(now))
                .toList();
        Map<RoleId, Role> roleMap = roles.findByIds(
                        tenantId,
                        effective.stream()
                                .map(PrincipalRoleBinding::roleId)
                                .collect(java.util.stream.Collectors.toSet()))
                .stream()
                .filter(Role::active)
                .collect(java.util.stream.Collectors.toMap(Role::roleId, value -> value));
        List<RolePermissionGrant> grants = rolePermissions.findByRoleIds(tenantId, roleMap.keySet());
        Set<PermissionCode> permissionCodesToResolve = grants.stream()
                .map(RolePermissionGrant::permissionCode)
                .collect(java.util.stream.Collectors.toSet());
        Map<PermissionCode, Permission> permissionMap = permissions.findByCodes(permissionCodesToResolve).stream()
                .filter(Permission::active)
                .collect(java.util.stream.Collectors.toMap(Permission::code, value -> value));
        Map<PermissionCode, List<EffectiveAccessSourceResponse>> sources =
                new TreeMap<>(Comparator.comparing(PermissionCode::value));
        for (RolePermissionGrant grant : grants) {
            Role role = roleMap.get(grant.roleId());
            Permission permission = permissionMap.get(grant.permissionCode());
            if (role == null || permission == null) {
                continue;
            }
            for (PrincipalRoleBinding binding : effective) {
                if (!binding.roleId().equals(grant.roleId())) {
                    continue;
                }
                if (!permission.supports(binding.scope().type()) || !scopeSnapshot.contains(binding.scope())) {
                    continue;
                }
                sources.computeIfAbsent(permission.code(), ignored -> new ArrayList<>())
                        .add(source(binding, role, userId));
            }
        }
        List<String> conflicts = new ArrayList<>();
        List<EffectivePermissionResponse> permissions = sources.entrySet().stream()
                .map(entry -> {
                    List<EffectiveAccessSourceResponse> ordered = entry.getValue().stream()
                            .sorted(Comparator.comparing(EffectiveAccessSourceResponse::roleName)
                                    .thenComparing(EffectiveAccessSourceResponse::scopeType)
                                    .thenComparing(EffectiveAccessSourceResponse::scopeId))
                            .toList();
                    List<String> observations = ordered.size() > 1
                            ? List.of("Permission is granted by multiple active sources; revoking one source may not remove access.")
                            : List.of();
                    if (ordered.stream()
                                    .map(value -> value.scopeType() + ":" + value.scopeId())
                                    .distinct()
                                    .count()
                            > 1) {
                        conflicts.add(entry.getKey().value() + " is effective through multiple scopes");
                    }
                    return new EffectivePermissionResponse(entry.getKey().value(), ordered, observations);
                })
                .toList();
        EffectiveAccessResponse access = new EffectiveAccessResponse(
                tenantId,
                userId,
                now,
                permissions,
                conflicts.stream().distinct().sorted().toList());
        Set<String> roleCodes = effective.stream()
                .filter(binding -> scopeSnapshot.contains(binding.scope()))
                .map(binding -> roleMap.get(binding.roleId()))
                .filter(Objects::nonNull)
                .map(role -> role.roleCode().value())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> permissionCodes = sources.keySet().stream()
                .map(PermissionCode::value)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Map<String, Set<String>> permissionScopes = new TreeMap<>();
        for (Map.Entry<PermissionCode, List<EffectiveAccessSourceResponse>> entry : sources.entrySet()) {
            Set<String> scopes = entry.getValue().stream()
                    .map(source -> source.scopeType() + ":" + source.scopeId())
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            permissionScopes.put(entry.getKey().value(), Set.copyOf(scopes));
        }
        return new IamEffectiveAuthorityResponse(
                tenantId, userId, now, roleCodes, permissionCodes, permissionScopes, access);
    }

    private RoleBindingAssignmentPreviewResponse previewAssignmentInTransaction(
            String tenantId,
            PreviewRoleBindingRequest request) {
        Instant now = clock.instant();
        Instant effective = request.effectiveAt() == null ? now : request.effectiveAt();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(effective)) {
            throw new IllegalArgumentException("ROLE_BINDING_EXPIRY_INVALID");
        }
        Role role = roles.findById(tenantId, new RoleId(request.roleId()))
                .orElseThrow(() -> new IllegalArgumentException("ROLE_NOT_FOUND"));
        if (!role.active()) {
            throw new IllegalArgumentException("ROLE_INACTIVE");
        }
        ScopeType scopeType = ScopeType.valueOf(request.scopeType().trim().toUpperCase(Locale.ROOT));
        if (scopeType == ScopeType.INSTANCE) {
            throw new IllegalArgumentException("TENANT_ROLE_BINDING_INSTANCE_SCOPE_NOT_ALLOWED");
        }
        new ScopeRef(scopeType, request.scopeId(), tenantId);
        Set<String> candidate = rolePermissions.findByRoleIds(tenantId, Set.of(role.roleId())).stream()
                .map(value -> value.permissionCode().value())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> existing = new TreeSet<>();
        List<String> warnings = new ArrayList<>();
        if (request.principalType() == PrincipalRef.PrincipalType.USER) {
            effectiveAccessInTransaction(tenantId, request.principalId()).permissions().stream()
                    .map(EffectivePermissionResponse::permissionCode)
                    .forEach(existing::add);
        } else if (request.principalType() == PrincipalRef.PrincipalType.DEPARTMENT) {
            warnings.add("Department Role Bindings are inherited by active members in the Department hierarchy.");
        } else if (request.principalType() == PrincipalRef.PrincipalType.GROUP) {
            warnings.add("Group Role Bindings are inherited by active members of this Group and its child Groups.");
        } else {
            warnings.add("Service Account access applies to the non-human principal only.");
        }
        Set<String> already = new TreeSet<>(candidate);
        already.retainAll(existing);
        Set<String> added = new TreeSet<>(candidate);
        added.removeAll(existing);
        if (candidate.isEmpty()) {
            warnings.add("The selected Role currently grants no permissions.");
        }
        if (added.isEmpty() && !candidate.isEmpty()) {
            warnings.add("The selected principal already has every permission granted by this Role.");
        }
        if (effective.isAfter(now)) {
            warnings.add("This Role Binding becomes effective in the future.");
        }
        if (request.expiresAt() != null) {
            warnings.add("This Role Binding expires automatically at " + request.expiresAt() + ".");
        }
        return new RoleBindingAssignmentPreviewResponse(
                tenantId,
                request.principalType().name(),
                request.principalId(),
                role.roleId().value(),
                role.roleName(),
                scopeType.name(),
                request.scopeId(),
                effective,
                request.expiresAt(),
                List.copyOf(added),
                List.copyOf(already),
                List.copyOf(warnings));
    }

    private RoleBindingRevocationPreviewResponse previewRevocationInTransaction(
            String tenantId,
            String userId,
            String bindingId) {
        PrincipalRoleBinding target = bindings.findById(tenantId, bindingId)
                .orElseThrow(() -> new IllegalArgumentException("ROLE_BINDING_NOT_FOUND"));
        Role role = roles.findById(tenantId, target.roleId())
                .orElseThrow(() -> new IllegalArgumentException("ROLE_NOT_FOUND"));
        EffectiveAccessResponse before = effectiveAccessInTransaction(tenantId, userId);
        Set<String> affected = before.permissions().stream()
                .filter(permission -> permission.sources().stream()
                        .anyMatch(source -> source.bindingId().equals(bindingId)))
                .map(EffectivePermissionResponse::permissionCode)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> retained = new TreeSet<>();
        for (EffectivePermissionResponse permission : before.permissions()) {
            if (!affected.contains(permission.permissionCode())) {
                continue;
            }
            boolean hasOther = permission.sources().stream()
                    .anyMatch(source -> !source.bindingId().equals(bindingId));
            if (hasOther) {
                retained.add(permission.permissionCode());
            }
        }
        Set<String> lost = new TreeSet<>(affected);
        lost.removeAll(retained);
        List<String> warnings = new ArrayList<>();
        if (target.principal().principalType() == PrincipalRef.PrincipalType.DEPARTMENT) {
            warnings.add("This binding is inherited from a Department and may affect active members in its subtree.");
        } else if (target.principal().principalType() == PrincipalRef.PrincipalType.GROUP) {
            warnings.add("This binding is inherited from a Group and may affect active members in child Groups.");
        }
        if (affected.isEmpty()) {
            warnings.add("This binding does not currently contribute effective access for the selected user.");
        } else if (lost.isEmpty()) {
            warnings.add("No effective permission is expected to disappear because equivalent active grants remain.");
        }
        return new RoleBindingRevocationPreviewResponse(
                bindingId,
                target.roleId().value(),
                role.roleName(),
                target.scope().type().name(),
                target.scope().scopeId(),
                List.copyOf(lost),
                List.copyOf(retained),
                List.copyOf(warnings));
    }

    private static EffectiveAccessSourceResponse source(
            PrincipalRoleBinding binding,
            Role role,
            String userId) {
        return new EffectiveAccessSourceResponse(
                binding.bindingId(),
                role.roleId().value(),
                role.roleName(),
                binding.principal().principalType().name(),
                binding.principal().principalId(),
                binding.scope().type().name(),
                binding.scope().scopeId(),
                binding.effectiveAt(),
                binding.expiresAt(),
                binding.principal().principalType() == PrincipalRef.PrincipalType.GROUP
                        && !binding.principal().principalId().equals(userId));
    }
}
