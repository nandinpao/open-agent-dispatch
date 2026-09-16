package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.rbac.event.RbacSecurityEvent;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** R7 fail-closed boundary for privilege escalation, effective SoD, critical approvals and evidence. */
public final class IamRbacHardeningService {
    private final PrincipalExpansionPort expansion;
    private final PrincipalRoleBindingRepository bindings;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final PermissionCatalogRepository permissions;
    private final RbacHardeningInspectionPort inspection;
    private final RbacCriticalApprovalRepository approvals;
    private final RbacChangeEvidencePort evidence;
    private final RbacEventPublisher events;
    private final Clock clock;
    private final TenantRbacExecutionPort execution;

    public IamRbacHardeningService(
            PrincipalExpansionPort expansion,
            PrincipalRoleBindingRepository bindings,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            PermissionCatalogRepository permissions,
            RbacHardeningInspectionPort inspection,
            RbacCriticalApprovalRepository approvals,
            RbacChangeEvidencePort evidence,
            RbacEventPublisher events,
            Clock clock,
            TenantRbacExecutionPort execution) {
        this.expansion = Objects.requireNonNull(expansion);
        this.bindings = Objects.requireNonNull(bindings);
        this.roles = Objects.requireNonNull(roles);
        this.rolePermissions = Objects.requireNonNull(rolePermissions);
        this.permissions = Objects.requireNonNull(permissions);
        this.inspection = Objects.requireNonNull(inspection);
        this.approvals = Objects.requireNonNull(approvals);
        this.evidence = Objects.requireNonNull(evidence);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
        this.execution = Objects.requireNonNull(execution);
    }

    public RbacHardeningPreviewResponse previewPermissionReplacement(
            String tenantId,
            String roleId,
            ReplaceRolePermissionsRequest request,
            IamApiRequestContext context) {
        return execution.read(
                tenantId,
                actor(context, "permission-replacement-preview"),
                () -> previewPermissionReplacementInTransaction(tenantId, roleId, request, context));
    }

    public RbacHardeningPreviewResponse previewBinding(
            String tenantId,
            BindRoleRequest request,
            IamApiRequestContext context) {
        return execution.read(
                tenantId,
                actor(context, "binding-preview"),
                () -> previewBindingInTransaction(tenantId, request, context));
    }

    public RbacHardeningPreviewResponse enforcePermissionReplacement(
            String tenantId,
            String roleId,
            ReplaceRolePermissionsRequest request,
            IamApiRequestContext context) {
        return execution.write(tenantId, actor(context, "permission-replacement-enforce"), () -> {
            RbacHardeningPreviewResponse preview =
                    previewPermissionReplacementInTransaction(tenantId, roleId, request, context);
            if (preview.conflicts().contains("GRANT_ABOVE_ACTOR")) {
                reject(tenantId, context, roleId, RbacReasonCode.RBAC_GRANT_ABOVE_ACTOR_FORBIDDEN);
            }
            return preview;
        });
    }

    public RbacHardeningPreviewResponse enforceBinding(
            String tenantId,
            BindRoleRequest request,
            IamApiRequestContext context) {
        return execution.write(tenantId, actor(context, "binding-enforce"), () -> {
            RbacHardeningPreviewResponse preview = previewBindingInTransaction(tenantId, request, context);
            if (preview.conflicts().contains("GRANT_ABOVE_ACTOR")) {
                reject(
                        tenantId,
                        context,
                        request.roleId(),
                        RbacReasonCode.RBAC_ASSIGNABLE_ROLE_BOUNDARY_VIOLATION);
            }
            if (preview.conflicts().contains("SELF_BINDING")) {
                reject(tenantId, context, request.roleId(), RbacReasonCode.RBAC_SELF_BINDING_FORBIDDEN);
            }
            if (preview.conflicts().contains("ROLE_HAS_NO_PERMISSIONS")
                    || preview.conflicts().stream().anyMatch(value -> value.startsWith("ROLE_SCOPE_UNSUPPORTED:"))) {
                reject(tenantId, context, request.roleId(), RbacReasonCode.ROLE_BINDING_SCOPE_INVALID);
            }
            if (preview.conflicts().stream().anyMatch(value -> value.startsWith("SOD:"))) {
                reject(
                        tenantId,
                        context,
                        request.roleId(),
                        RbacReasonCode.ROLE_SEPARATION_OF_DUTIES_CONFLICT);
            }
            return preview;
        });
    }

    public RbacCriticalApprovalResponse requestPermissionApproval(
            String tenantId,
            String roleId,
            ReplaceRolePermissionsRequest request,
            IamApiRequestContext context) {
        return execution.write(tenantId, actor(context, "permission-approval-request"), () ->
                requestApproval(
                        tenantId,
                        "ROLE",
                        roleId,
                        RbacApprovalOperation.ROLE_PERMISSION_REPLACE,
                        previewPermissionReplacementInTransaction(tenantId, roleId, request, context),
                        context));
    }

    public RbacCriticalApprovalResponse requestBindingApproval(
            String tenantId,
            BindRoleRequest request,
            IamApiRequestContext context) {
        return execution.write(tenantId, actor(context, "binding-approval-request"), () ->
                requestApproval(
                        tenantId,
                        "ROLE_BINDING",
                        request.authorizationTarget(),
                        RbacApprovalOperation.ROLE_BINDING_CREATE,
                        previewBindingInTransaction(tenantId, request, context),
                        context));
    }

    public RbacCriticalApprovalResponse approval(String tenantId, String approvalId) {
        return execution.read(
                tenantId,
                "iam-api:rbac-approval-read",
                () -> RbacCriticalApprovalResponse.from(requireApproval(authorityScope(tenantId), approvalId)));
    }

    public List<RbacCriticalApprovalResponse> approvals(String tenantId, String status, int limit) {
        return execution.read(
                tenantId,
                "iam-api:rbac-approval-list",
                () -> approvals.findRecent(authorityScope(tenantId), status, limit).stream()
                        .map(RbacCriticalApprovalResponse::from)
                        .toList());
    }

    public RbacCriticalApprovalResponse approve(
            String tenantId,
            String approvalId,
            String reason,
            IamApiRequestContext context) {
        return execution.write(tenantId, actor(context, "rbac-approval-approve"), () -> {
            RbacCriticalChangeApproval current = requireApproval(authorityScope(tenantId), approvalId);
            RbacCriticalChangeApproval next = current.approve(context.actorId(), reason, clock.instant());
            approvals.save(next, current.version());
            publish(
                    "RBAC_CRITICAL_APPROVAL_APPROVED",
                    authorityScope(tenantId),
                    context.actorId(),
                    current.targetId(),
                    "",
                    context.correlationId(),
                    clock.instant());
            return RbacCriticalApprovalResponse.from(next);
        });
    }

    public RbacCriticalApprovalResponse reject(
            String tenantId,
            String approvalId,
            String reason,
            IamApiRequestContext context) {
        return execution.write(tenantId, actor(context, "rbac-approval-reject"), () -> {
            RbacCriticalChangeApproval current = requireApproval(authorityScope(tenantId), approvalId);
            RbacCriticalChangeApproval next = current.reject(context.actorId(), reason, clock.instant());
            approvals.save(next, current.version());
            publish(
                    "RBAC_CRITICAL_APPROVAL_REJECTED",
                    authorityScope(tenantId),
                    context.actorId(),
                    current.targetId(),
                    "",
                    context.correlationId(),
                    clock.instant());
            return RbacCriticalApprovalResponse.from(next);
        });
    }

    public void recordEvidence(
            String tenantId,
            String targetType,
            String targetId,
            RbacHardeningPreviewResponse preview,
            String approvalId,
            IamApiRequestContext context) {
        execution.write(tenantId, actor(context, "rbac-evidence-record"), () -> {
            RbacHardeningPreviewResponse p = preview;
            evidence.append(new RbacChangeEvidence(
                    "rbe-" + UUID.randomUUID(),
                    authorityScope(tenantId),
                    p.operation(),
                    context.actorId(),
                    targetType,
                    targetId,
                    approvalId,
                    "{\"permissions\":" + json(p.beforePermissions()) + "}",
                    "{\"permissions\":" + json(p.afterPermissions()) + "}",
                    p.addedPermissions(),
                    p.removedPermissions(),
                    p.warnings(),
                    context.correlationId(),
                    context.requireAuditReason(),
                    clock.instant()));
            return null;
        });
    }

    public void consumeApprovalForMutation(
            String tenantId,
            String approvalId,
            RbacApprovalOperation operation,
            RbacHardeningPreviewResponse preview,
            IamApiRequestContext context) {
        execution.write(tenantId, actor(context, "rbac-approval-consume"), () -> {
            if (!preview.critical()) {
                return null;
            }
            if (approvalId == null || approvalId.isBlank()) {
                throw new RbacDomainException(
                        RbacReasonCode.RBAC_CRITICAL_APPROVAL_REQUIRED,
                        "Critical RBAC change requires two-person approval");
            }
            RbacCriticalChangeApproval current = requireApproval(authorityScope(tenantId), approvalId);
            RbacCriticalChangeApproval consumed = current.consume(
                    context.actorId(), operation, preview.requestHash(), clock.instant());
            approvals.save(consumed, current.version());
            return null;
        });
    }

    private RbacHardeningPreviewResponse previewPermissionReplacementInTransaction(
            String tenantId,
            String roleId,
            ReplaceRolePermissionsRequest request,
            IamApiRequestContext context) {
        Role role = requireRole(tenantId, roleId);
        Set<String> before = permissionsForRole(tenantId, role.roleId());
        Set<String> after = sorted(request.permissionCodes());
        Set<String> added = difference(after, before);
        Set<String> removed = difference(before, after);
        ScopeRef targetScope = tenantId == null || tenantId.isBlank()
                ? ScopeRef.instance()
                : ScopeRef.tenant(tenantId);
        Set<String> actorPermissions = effectiveActorPermissions(tenantId, context, targetScope);
        boolean root = isRoot(context);
        boolean above = !root && !actorPermissions.containsAll(after);
        boolean critical = containsCritical(after);
        List<String> conflicts = new ArrayList<>();
        if (above) {
            conflicts.add("GRANT_ABOVE_ACTOR");
        }
        return new RbacHardeningPreviewResponse(
                "ROLE_PERMISSION_REPLACE",
                hash("ROLE_PERMISSION_REPLACE", tenantId, roleId, after),
                critical,
                critical,
                above,
                List.copyOf(before),
                List.copyOf(after),
                List.copyOf(added),
                List.copyOf(removed),
                conflicts,
                List.of());
    }

    private RbacHardeningPreviewResponse previewBindingInTransaction(
            String tenantId,
            BindRoleRequest request,
            IamApiRequestContext context) {
        Role role = requireRole(tenantId, request.roleId());
        Set<String> rolePermissions = permissionsForRole(tenantId, role.roleId());
        ScopeRef scope = scope(tenantId, request.scopeType(), request.scopeId());
        Set<String> actorPermissions = effectiveActorPermissions(tenantId, context, scope);
        boolean root = isRoot(context);
        boolean above = !root && !actorPermissions.containsAll(rolePermissions);
        boolean self = selfBinding(tenantId, request, context);
        Instant evaluationAt = request.effectiveAt() == null ? clock.instant() : request.effectiveAt();
        Set<String> before = effectivePrincipalPermissions(
                tenantId,
                request.principalType(),
                request.principalId(),
                scope,
                evaluationAt);
        Set<String> after = new TreeSet<>(before);
        after.addAll(rolePermissions);
        Set<String> added = difference(after, before);
        List<String> sod = effectiveSodConflicts(
                tenantId,
                request.principalType(),
                request.principalId(),
                role.roleId(),
                scope,
                evaluationAt);
        List<String> conflicts = new ArrayList<>();
        if (above) {
            conflicts.add("GRANT_ABOVE_ACTOR");
        }
        if (self) {
            conflicts.add("SELF_BINDING");
        }
        Set<PermissionCode> rolePermissionCodes = rolePermissions.stream().map(PermissionCode::new)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (rolePermissionCodes.isEmpty()) {
            conflicts.add("ROLE_HAS_NO_PERMISSIONS");
        }
        Map<PermissionCode, Permission> rolePermissionDefinitions = permissions.findByCodes(rolePermissionCodes).stream()
                .collect(java.util.stream.Collectors.toMap(Permission::code, value -> value));
        List<String> unsupportedForScope = rolePermissionCodes.stream()
                .filter(code -> {
                    Permission definition = rolePermissionDefinitions.get(code);
                    return definition == null || !definition.allowedScopes().contains(scope.type());
                })
                .map(PermissionCode::value)
                .sorted()
                .toList();
        if (!unsupportedForScope.isEmpty()) {
            conflicts.add("ROLE_SCOPE_UNSUPPORTED:" + String.join(",", unsupportedForScope));
        }
        conflicts.addAll(sod);
        boolean critical = containsCritical(rolePermissions);
        return new RbacHardeningPreviewResponse(
                "ROLE_BINDING_CREATE",
                hash(
                        "ROLE_BINDING_CREATE",
                        tenantId,
                        request.principalType() + ":" + request.principalId() + ":" + request.roleId()
                                + ":" + scope.type() + ":" + scope.scopeId(),
                        after),
                critical,
                critical,
                above || self,
                List.copyOf(before),
                List.copyOf(after),
                List.copyOf(added),
                List.of(),
                conflicts,
                request.principalType() == PrincipalRef.PrincipalType.GROUP
                        ? List.of("GROUP_HIERARCHY_INHERITANCE_EXPANDS_TO_ACTIVE_DESCENDANT_MEMBERS")
                        : request.principalType() == PrincipalRef.PrincipalType.DEPARTMENT
                        ? List.of("DEPARTMENT_INHERITANCE_EXPANDS_TO_ACTIVE_SUBTREE_MEMBERS")
                        : List.of());
    }

    private RbacCriticalApprovalResponse requestApproval(
            String tenantId,
            String targetType,
            String targetId,
            RbacApprovalOperation operation,
            RbacHardeningPreviewResponse preview,
            IamApiRequestContext context) {
        if (!preview.critical()) {
            throw new RbacDomainException(
                    RbacReasonCode.RBAC_CRITICAL_APPROVAL_STATE_INVALID,
                    "Approval is only required for critical RBAC changes");
        }
        if (!preview.conflicts().isEmpty()) {
            throw new RbacDomainException(
                    RbacReasonCode.RBAC_SELF_ESCALATION_FORBIDDEN,
                    "Resolve hardening conflicts before requesting approval");
        }
        Instant at = clock.instant();
        String id = "rba-" + UUID.randomUUID();
        String authorityScope = authorityScope(tenantId);
        RbacCriticalChangeApproval approval = RbacCriticalChangeApproval.request(
                id,
                authorityScope,
                operation,
                preview.requestHash(),
                context.actorId(),
                targetType,
                targetId,
                at,
                at.plus(Duration.ofMinutes(30)));
        approvals.save(approval, 0);
        publish(
                "RBAC_CRITICAL_APPROVAL_REQUESTED",
                authorityScope,
                context.actorId(),
                targetId,
                "",
                context.correlationId(),
                at);
        return RbacCriticalApprovalResponse.from(approval);
    }

    private Set<String> effectiveActorPermissions(
            String tenantId,
            IamApiRequestContext context,
            ScopeRef targetScope) {
        if (isRoot(context)) {
            return permissions.findActiveCodes();
        }
        PrincipalRef actor = context.requireAuthentication().principal();
        Set<PrincipalRef> principals = new LinkedHashSet<>(expansion.expand(tenantId, actor));
        principals.add(actor);
        List<PrincipalRoleBinding> effective = bindings.findEffective(tenantId, principals, clock.instant()).stream()
                .filter(binding -> inspection.scopeContains(tenantId, binding.scope(), targetScope))
                .toList();
        Set<RoleId> roleIds = new LinkedHashSet<>();
        effective.forEach(binding -> roleIds.add(binding.roleId()));
        Map<RoleId, Set<String>> byRole = new HashMap<>();
        for (RolePermissionGrant grant : rolePermissions.findByRoleIds(tenantId, roleIds)) {
            byRole.computeIfAbsent(grant.roleId(), ignored -> new TreeSet<>())
                    .add(grant.permissionCode().value());
        }
        Set<String> allowed = new TreeSet<>();
        for (PrincipalRoleBinding binding : effective) {
            allowed.addAll(byRole.getOrDefault(binding.roleId(), Set.of()));
        }
        return allowed;
    }

    private Set<String> effectivePrincipalPermissions(
            String tenantId,
            PrincipalRef.PrincipalType type,
            String id,
            ScopeRef targetScope,
            Instant at) {
        PrincipalRef target = new PrincipalRef(type, id);
        Set<PrincipalRef> principals = new LinkedHashSet<>();
        principals.add(target);
        if (type == PrincipalRef.PrincipalType.USER) {
            principals.addAll(expansion.expand(tenantId, target));
        }
        List<PrincipalRoleBinding> effective = bindings.findEffective(tenantId, principals, at).stream()
                .filter(binding -> inspection.scopeContains(tenantId, binding.scope(), targetScope))
                .toList();
        Set<RoleId> roleIds = new LinkedHashSet<>();
        effective.forEach(binding -> roleIds.add(binding.roleId()));
        Map<RoleId, Set<String>> byRole = new HashMap<>();
        for (RolePermissionGrant grant : rolePermissions.findByRoleIds(tenantId, roleIds)) {
            byRole.computeIfAbsent(grant.roleId(), ignored -> new TreeSet<>())
                    .add(grant.permissionCode().value());
        }
        Set<String> allowed = new TreeSet<>();
        for (PrincipalRoleBinding binding : effective) {
            allowed.addAll(byRole.getOrDefault(binding.roleId(), Set.of()));
        }
        return allowed;
    }

    private List<String> effectiveSodConflicts(
            String tenantId,
            PrincipalRef.PrincipalType type,
            String id,
            RoleId candidate,
            ScopeRef scope,
            Instant at) {
        Set<String> users = switch (type) {
            case USER -> Set.of(id);
            case DEPARTMENT -> inspection.activeUserIdsForDepartment(tenantId, id, at);
            case GROUP -> inspection.activeUserIdsForGroup(tenantId, id, at);
            default -> Set.of();
        };
        List<SeparationOfDutiesRule> rules = inspection.activeSeparationOfDutiesRules(tenantId);
        List<String> result = new ArrayList<>();
        for (String user : users) {
            PrincipalRef principal = new PrincipalRef(PrincipalRef.PrincipalType.USER, user);
            Set<PrincipalRef> principals = new LinkedHashSet<>(expansion.expand(tenantId, principal));
            principals.add(principal);
            for (PrincipalRoleBinding existing : bindings.findEffective(tenantId, principals, at)) {
                for (SeparationOfDutiesRule rule : rules) {
                    if (rule.conflicts(candidate, existing.roleId())
                            && (!rule.scopeOverlapRequired()
                            || inspection.scopesOverlap(tenantId, scope, existing.scope()))) {
                        result.add("SOD:" + rule.ruleId() + ":" + user);
                    }
                }
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    private boolean selfBinding(String tenantId, BindRoleRequest request, IamApiRequestContext context) {
        if (isRoot(context)) {
            return false;
        }
        String actor = context.actorId();
        if (request.principalType() == PrincipalRef.PrincipalType.USER) {
            return actor.equals(request.principalId());
        }
        if (request.principalType() == PrincipalRef.PrincipalType.DEPARTMENT
                || request.principalType() == PrincipalRef.PrincipalType.GROUP) {
            return expansion.expand(tenantId,new PrincipalRef(PrincipalRef.PrincipalType.USER,actor))
                    .contains(new PrincipalRef(request.principalType(),request.principalId()));
        }
        return false;
    }

    private Role requireRole(String tenantId, String roleId) {
        return roles.findById(tenantId, new RoleId(roleId))
                .orElseThrow(() -> new RbacDomainException(
                        RbacReasonCode.ROLE_NOT_FOUND,
                        "Role not found"));
    }

    private RbacCriticalChangeApproval requireApproval(String tenantId, String approvalId) {
        return approvals.findById(tenantId, approvalId)
                .orElseThrow(() -> new RbacDomainException(
                        RbacReasonCode.RBAC_CRITICAL_APPROVAL_NOT_FOUND,
                        "Critical approval not found"));
    }

    private Set<String> permissionsForRole(String tenantId, RoleId roleId) {
        return rolePermissions.findByRoleIds(tenantId, Set.of(roleId)).stream()
                .map(grant -> grant.permissionCode().value())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean containsCritical(Set<String> codes) {
        Set<PermissionCode> permissionCodes = codes.stream()
                .map(PermissionCode::new)
                .collect(java.util.stream.Collectors.toSet());
        return permissions.findByCodes(permissionCodes).stream()
                .anyMatch(permission -> permission.riskLevel() == Permission.RiskLevel.CRITICAL);
    }

    private ScopeRef scope(String tenantId, String raw, String scopeId) {
        ScopeType type = ScopeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        return switch (type) {
            case INSTANCE -> ScopeRef.instance();
            case TENANT -> ScopeRef.tenant(tenantId);
            case DEPARTMENT -> ScopeRef.department(tenantId, scopeId);
            case DEPARTMENT_SUBTREE -> ScopeRef.departmentSubtree(tenantId, scopeId);
            case GROUP -> ScopeRef.group(tenantId, scopeId);
        };
    }

    private static String authorityScope(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "INSTANCE" : tenantId.trim();
    }

    private boolean isRoot(IamApiRequestContext context) {
        return context.requireAuthentication().principal().principalType()
                == PrincipalRef.PrincipalType.INSTANCE_ROOT;
    }

    private String actor(IamApiRequestContext context, String fallback) {
        if (context == null) {
            return "iam-api:" + fallback;
        }
        return context.authentication()
                .map(authentication -> authentication.subject().subjectId())
                .orElse("iam-api:" + fallback);
    }

    private void reject(
            String tenantId,
            IamApiRequestContext context,
            String roleId,
            RbacReasonCode code) {
        publish(
                "RBAC_HARDENING_REJECTED",
                tenantId,
                context.actorId(),
                roleId,
                code.name(),
                context.correlationId(),
                clock.instant());
        throw new RbacDomainException(code, code.name());
    }

    private void publish(
            String type,
            String tenant,
            String actor,
            String target,
            String reason,
            String correlationId,
            Instant at) {
        events.publish(new RbacSecurityEvent(
                UUID.randomUUID().toString(),
                type,
                tenant,
                actor,
                "",
                target,
                "",
                reason,
                Map.of("correlationId", correlationId),
                at));
    }

    private static Set<String> sorted(Collection<String> values) {
        return values == null ? new TreeSet<>() : new TreeSet<>(values);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static String hash(
            String operation,
            String tenant,
            String target,
            Set<String> permissionCodes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = operation + "|" + tenant + "|" + target + "|"
                    + String.join(",", permissionCodes);
            return java.util.HexFormat.of().formatHex(
                    digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String json(List<String> values) {
        return "[" + values.stream()
                .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }
}
