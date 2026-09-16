package com.opensocket.aievent.core.iam.rbac.application.service;

import com.opensocket.aievent.core.iam.rbac.application.command.*;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.rbac.event.RbacSecurityEvent;
import java.time.*;
import java.util.*;

public final class RbacAdministrationService implements RbacAdministrationPort {
    private final RoleRepository roles;
    private final PermissionCatalogRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final PrincipalRoleBindingRepository bindings;
    private final PolicyVersionRepository policyVersions;
    private final RbacEventPublisher events;
    private final CacheInvalidationPort cacheInvalidation;
    private final Clock clock;

    public RbacAdministrationService(RoleRepository roles, PermissionCatalogRepository permissions,
                                     RolePermissionRepository rolePermissions,
                                     PrincipalRoleBindingRepository bindings,
                                     PolicyVersionRepository policyVersions,
                                     RbacEventPublisher events,
                                     CacheInvalidationPort cacheInvalidation, Clock clock) {
        this.roles = Objects.requireNonNull(roles, "roles");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.rolePermissions = Objects.requireNonNull(rolePermissions, "rolePermissions");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.policyVersions = Objects.requireNonNull(policyVersions, "policyVersions");
        this.events = Objects.requireNonNull(events, "events");
        this.cacheInvalidation = Objects.requireNonNull(cacheInvalidation, "cacheInvalidation");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Role createRole(CreateRoleCommand command) {
        Instant at = time(command.occurredAt());
        String tenantId = normalizeTenant(command.tenantId());
        RoleCode code = new RoleCode(command.roleCode());
        if (command.platformRole() != tenantId.isEmpty()) {
            throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_SCOPE_INVALID,
                    "Platform roles must not have tenantId; Tenant roles require tenantId");
        }
        if (roles.findByCode(tenantId, code).isPresent()) {
            throw new RbacDomainException(RbacReasonCode.ROLE_CODE_CONFLICT, "Role code already exists");
        }
        Role role = command.platformRole()
                ? Role.customPlatformRole(new RoleId(command.roleId()), code, command.roleName(),
                    command.description(), command.actorId(), at)
                : Role.customTenantRole(new RoleId(command.roleId()), tenantId, code, command.roleName(),
                    command.description(), command.actorId(), at);
        roles.save(role, 0);
        publish("ROLE_CREATED", tenantId, command.actorId(), "", role.roleId().value(), "",
                command.auditReason(), at);
        invalidate(tenantId, Set.of(), at);
        return role;
    }

    @Override
    public Role updateRole(UpdateRoleCommand command) {
        Instant at = time(command.occurredAt());
        String tenantId = normalizeTenant(command.tenantId());
        Role current = role(tenantId, command.roleId());
        Role updated = current.update(command.roleName(), command.description(), command.actorId(), at);
        roles.save(updated, command.expectedVersion());
        publish("ROLE_UPDATED", tenantId, command.actorId(), "", current.roleId().value(), "",
                command.auditReason(), at);
        invalidate(tenantId, Set.of(), at);
        return updated;
    }

    @Override
    public Role changeRoleStatus(ChangeRoleStatusCommand command) {
        Instant at = time(command.occurredAt());
        String tenantId = normalizeTenant(command.tenantId());
        Role current = role(tenantId, command.roleId());
        RoleStatus next;
        try { next = RoleStatus.valueOf(command.status().trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) {
            throw new RbacDomainException(RbacReasonCode.ROLE_STATUS_TRANSITION_INVALID,
                    "Unknown Role status");
        }
        Role updated = current.changeStatus(next, command.actorId(), at);
        roles.save(updated, command.expectedVersion());
        publish("ROLE_STATUS_CHANGED", tenantId, command.actorId(), "", current.roleId().value(), "",
                command.auditReason(), at);
        invalidate(tenantId, Set.of(), at);
        return updated;
    }

    @Override
    public void replacePermissions(ReplaceRolePermissionsCommand command) {
        Instant at = time(command.occurredAt());
        String tenantId = normalizeTenant(command.tenantId());
        Role role = role(tenantId, command.roleId());
        ensureMutableActive(role);
        Set<String> requested = command.permissionCodes() == null ? Set.of() : new TreeSet<>(command.permissionCodes());
        List<RolePermissionGrant> grants = new ArrayList<>();
        for (String code : requested) {
            Permission permission = permission(code);
            validatePermissionForRole(role, permission);
            grants.add(new RolePermissionGrant(UUID.randomUUID().toString(), optionalTenant(tenantId),
                    role.roleId(), permission.code(), at, command.actorId(), 1));
        }
        Role versioned = role.update(role.roleName(), role.description(), command.actorId(), at);
        roles.save(versioned, command.expectedRoleVersion());
        rolePermissions.replace(tenantId, role.roleId(), grants);
        publish("ROLE_PERMISSION_MATRIX_REPLACED", tenantId, command.actorId(), "", role.roleId().value(),
                "", command.auditReason(), at);
        invalidate(tenantId, Set.of(), at);
    }

    @Override
    public Role resolveRoleByCode(String tenantId, String roleCode) {
        String normalizedTenant = normalizeTenant(tenantId);
        RoleCode code = new RoleCode(roleCode);
        return roles.findByCode(normalizedTenant, code)
                .orElseThrow(() -> new RbacDomainException(
                        RbacReasonCode.ROLE_NOT_FOUND,
                        "Role not found for code " + code.value()));
    }

    @Override
    public PrincipalRoleBinding bindRole(BindRoleCommand command) {
        Instant at = time(command.occurredAt());
        String tenantId = normalizeTenant(command.tenantId());
        Role role = role(tenantId, command.roleId());
        ScopeType type = parseScopeType(command.scopeType());
        ScopeRef scope = scope(tenantId, type, command.scopeId());
        validateRoleBinding(role, type, command.principal());
        PrincipalRoleBinding binding = PrincipalRoleBinding.create(command.bindingId(), command.principal(),
                role.roleId(), scope, command.effectiveAt() == null ? at : command.effectiveAt(),
                command.expiresAt(), command.actorId(), at);
        bindings.save(binding, 0);
        publish("ROLE_BINDING_ADDED", tenantId, command.actorId(), command.principal().principalId(),
                role.roleId().value(), "", command.auditReason(), at);
        invalidate(tenantId, invalidationPrincipals(command.principal()), at);
        return binding;
    }

    @Override
    public PrincipalRoleBinding changeBinding(ChangeRoleBindingCommand command) {
        Instant at = time(command.occurredAt());
        String tenantId = normalizeTenant(command.tenantId());
        PrincipalRoleBinding current = bindings.findById(tenantId, command.bindingId())
                .orElseThrow(() -> new RbacDomainException(RbacReasonCode.ROLE_BINDING_NOT_FOUND,
                        "Role binding not found"));
        if (!current.principal().equals(command.expectedPrincipal())) {
            throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_PRINCIPAL_FORBIDDEN,
                    "Role binding principal does not match the Service Account being updated");
        }
        Role role = role(tenantId, command.roleId());
        ScopeType type = parseScopeType(command.scopeType());
        ScopeRef scope = scope(tenantId, type, command.scopeId());
        validateRoleBinding(role, type, command.expectedPrincipal());
        PrincipalRoleBinding updated = current.changeResponsibility(role.roleId(), scope, command.actorId(), at);
        bindings.save(updated, current.version());
        publish("ROLE_BINDING_RESPONSIBILITY_CHANGED", tenantId, command.actorId(), current.principal().principalId(),
                role.roleId().value(), "", command.auditReason(), at);
        invalidate(tenantId, invalidationPrincipals(current.principal()), at);
        return updated;
    }

    @Override
    public void revokeBinding(RevokeRoleBindingCommand command) {
        Instant at = time(command.occurredAt());
        String tenantId = normalizeTenant(command.tenantId());
        PrincipalRoleBinding current = bindings.findById(tenantId, command.bindingId())
                .orElseThrow(() -> new RbacDomainException(RbacReasonCode.ROLE_BINDING_NOT_FOUND,
                        "Role binding not found"));
        PrincipalRoleBinding revoked = current.revoke(command.actorId(), at);
        bindings.save(revoked, command.expectedVersion());
        publish("ROLE_BINDING_REMOVED", tenantId, command.actorId(), current.principal().principalId(),
                current.roleId().value(), "", command.reason(), at);
        invalidate(tenantId, invalidationPrincipals(current.principal()), at);
    }

    private ScopeType parseScopeType(String value) {
        try { return ScopeType.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) {
            throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_SCOPE_INVALID, "Unknown scope type");
        }
    }

    private ScopeRef scope(String tenantId, ScopeType type, String scopeId) {
        return switch (type) {
            case INSTANCE -> ScopeRef.instance();
            case TENANT -> ScopeRef.tenant(tenantId);
            case DEPARTMENT -> ScopeRef.department(tenantId, scopeId);
            case DEPARTMENT_SUBTREE -> ScopeRef.departmentSubtree(tenantId, scopeId);
            case GROUP -> ScopeRef.group(tenantId, scopeId);
        };
    }

    private void validateRoleBinding(Role role, ScopeType type,
                                     com.opensocket.aievent.core.iam.security.contract.PrincipalRef principal) {
        if (role.platformOwned() != (type == ScopeType.INSTANCE)) {
            throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_SCOPE_INVALID,
                    "Platform roles require INSTANCE scope; Tenant roles cannot use INSTANCE scope");
        }
        Set<PermissionCode> rolePermissionCodes = rolePermissions.findByRoleIds(normalizeTenant(role.tenantId().orElse("")), Set.of(role.roleId())).stream()
                .map(RolePermissionGrant::permissionCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (rolePermissionCodes.isEmpty()) {
            throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_SCOPE_INVALID,
                    "Responsibility has no active permissions and cannot be assigned");
        }
        Map<PermissionCode, Permission> rolePermissionDefinitions = permissions.findByCodes(rolePermissionCodes).stream()
                .collect(java.util.stream.Collectors.toMap(Permission::code, value -> value));
        List<String> incompatiblePermissions = rolePermissionCodes.stream()
                .filter(code -> {
                    Permission definition = rolePermissionDefinitions.get(code);
                    return definition == null || !definition.allowedScopes().contains(type);
                })
                .map(PermissionCode::value).sorted().toList();
        if (!incompatiblePermissions.isEmpty()) {
            throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_SCOPE_INVALID,
                    "Role contains unknown or incompatible permissions for " + type + " scope: " + incompatiblePermissions);
        }
        if ((role.roleCode().value().equals("TENANT_ADMIN") || role.roleCode().value().equals("SYSTEM_ADMIN"))
                && principal.principalType()
                != com.opensocket.aievent.core.iam.security.contract.PrincipalRef.PrincipalType.USER) {
            throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_PRINCIPAL_FORBIDDEN,
                    "Administrator roles must be bound to a Human User");
        }
    }

    private Set<String> invalidationPrincipals(com.opensocket.aievent.core.iam.security.contract.PrincipalRef principal) {
        return principal.principalType() == com.opensocket.aievent.core.iam.security.contract.PrincipalRef.PrincipalType.GROUP
                || principal.principalType() == com.opensocket.aievent.core.iam.security.contract.PrincipalRef.PrincipalType.DEPARTMENT
                ? Set.of() : Set.of(principal.principalId());
    }

    private Role role(String tenantId, String roleId) {
        return roles.findById(tenantId, new RoleId(roleId))
                .orElseThrow(() -> new RbacDomainException(RbacReasonCode.ROLE_NOT_FOUND, "Role not found"));
    }

    private Permission permission(String code) {
        return permissions.findByCode(new PermissionCode(code))
                .orElseThrow(() -> new RbacDomainException(RbacReasonCode.AUTH_PERMISSION_UNKNOWN,
                        "Permission not found"));
    }

    private void ensureMutableActive(Role role) {
        if (!role.active()) throw new RbacDomainException(RbacReasonCode.ROLE_DISABLED, "Role is disabled");
        if (role.systemManaged()) throw new RbacDomainException(RbacReasonCode.ROLE_SYSTEM_MANAGED,
                "System-managed role permissions are immutable at runtime");
    }

    private void validatePermissionForRole(Role role, Permission permission) {
        if (role.platformOwned()) {
            if (!permission.allowedScopes().contains(ScopeType.INSTANCE)) {
                throw new RbacDomainException(RbacReasonCode.ROLE_PERMISSION_SCOPE_UNSUPPORTED,
                        "Platform roles may contain only INSTANCE permissions");
            }
        } else if (permission.allowedScopes().contains(ScopeType.INSTANCE)) {
            throw new RbacDomainException(RbacReasonCode.ROLE_INSTANCE_PERMISSION_FORBIDDEN,
                    "Tenant roles cannot contain INSTANCE permissions");
        }
    }

    private void invalidate(String tenantId, Set<String> principalIds, Instant at) {
        String scope = tenantId.isBlank() ? null : tenantId;
        long policyVersion = policyVersions.current(scope).value();
        cacheInvalidation.publish(new CacheInvalidationPort.CacheInvalidation(
                UUID.randomUUID().toString(), scope == null ? "INSTANCE" : scope,
                principalIds, policyVersion, 0, at));
    }

    private void publish(String type, String tenant, String actor, String principal, String role,
                         String permission, String reason, Instant at) {
        events.publish(new RbacSecurityEvent(UUID.randomUUID().toString(), type,
                tenant.isBlank() ? "INSTANCE" : tenant, actor, principal, role,
                permission, reason == null ? "" : reason, Map.of(), at));
    }

    private static String normalizeTenant(String tenantId) { return tenantId == null ? "" : tenantId.trim(); }
    private static Optional<String> optionalTenant(String tenantId) {
        return tenantId.isBlank() ? Optional.empty() : Optional.of(tenantId);
    }
    private Instant time(Instant at) { return at == null ? clock.instant() : at; }
}
