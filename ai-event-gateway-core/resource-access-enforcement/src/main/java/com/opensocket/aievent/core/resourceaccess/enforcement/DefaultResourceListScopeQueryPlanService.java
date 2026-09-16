package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Canonical P2.3B list-scope compiler shared by Admin, Dispatch and A2A resource domains.
 * It converges IAM organization scope with Resource Access grants and explicit denies.
 */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public final class DefaultResourceListScopeQueryPlanService implements ResourceListScopeQueryPlanPort {
    private final ResourceDecisionEvidenceRepository evidence;
    private final ResourcePermissionAuthorityPort permissions;
    private final ResourceEnforcementContextPort contexts;
    private final ResourceScopeShareRepositoryPort shares;

    public DefaultResourceListScopeQueryPlanService(ResourceDecisionEvidenceRepository evidence,
            ResourcePermissionAuthorityPort permissions, ResourceEnforcementContextPort contexts,
            ResourceScopeShareRepositoryPort shares) {
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.shares = Objects.requireNonNull(shares, "shares");
    }

    @Override
    public ResourceListScopeQueryPlan build(String permissionCode, ResourceType resourceType,
            VisibilityLevel requestedVisibility, String purpose) {
        Objects.requireNonNull(resourceType, "resourceType");
        ResourceEnforcementContext runtime = contexts.current();
        var auth = runtime.authentication();
        String tenant = auth.activeTenant().tenantId();
        Instant at = runtime.requestedAt();
        PolicyPrincipalRef direct = directPolicyPrincipal(auth.principal());
        PrincipalScopeSnapshot principalScope = evidence.resolvePrincipalScope(tenant, auth.principal(), at);
        ResourceRef synthetic = new ResourceRef(tenant, resourceType, "__RESOURCE_LIST_SCOPE__");
        AuthorizationRequest permissionRequest = new AuthorizationRequest(auth.principal(), auth, auth.activeTenant(),
                new ResourceAction(required(permissionCode, "permissionCode"), ResourceAction.ActionKind.READ, false),
                synthetic, requestedVisibility == null ? VisibilityLevel.METADATA : requestedVisibility,
                RequestChannel.INTERNAL_PORT, required(purpose, "purpose"), OperationPhase.START, "",
                runtime.correlationId(), SecurityEpoch.ZERO,
                Map.of("queryPlan", "RESOURCE_LIST", "resourceType", resourceType.name()));
        ResourcePermissionScopeDecision permission = permissions.resolveEffectiveScopes(permissionRequest, principalScope);
        PolicyVersion policyVersion = evidence.currentPolicyVersion(tenant);
        SecurityEpoch epoch = evidence.currentSecurityEpoch(tenant, auth.principal(), synthetic);
        if (!permission.granted()) return deny(tenant, direct, permissionCode, resourceType, policyVersion, epoch);

        Set<PolicyPrincipalRef> principals = policyPrincipals(auth.principal(), principalScope);
        List<ScopeGrantRecord> grants = evidence.findEffectiveScopeGrants(tenant, principals, permissionCode, resourceType, at);
        List<ExplicitDenyRecord> denies = evidence.findEffectiveExplicitDenies(tenant, principals, permissionCode, resourceType, at);
        Set<String> sharedResourceIds = shares.findEffectiveSharedResourceIds(tenant, resourceType, permission, at);

        Set<String> exact = new LinkedHashSet<>();
        Set<String> subtrees = new LinkedHashSet<>();
        Set<String> groups = new LinkedHashSet<>();
        Set<String> resources = new LinkedHashSet<>(sharedResourceIds);
        Set<String> excluded = new LinkedHashSet<>();
        Set<String> deniedDepartments = new LinkedHashSet<>();
        Set<String> deniedSubtrees = new LinkedHashSet<>();
        Set<String> deniedGroups = new LinkedHashSet<>();
        boolean tenantWide = permission.tenantScoped();
        VisibilityLevel cap = VisibilityLevel.NONE;
        exact.addAll(permission.exactDepartmentIds());
        subtrees.addAll(permission.subtreeDepartmentRootIds());
        groups.addAll(permission.groupIds());
        for (ScopeGrantRecord grant : grants) {
            if (IamScopeAuthorityCeiling.explicitResourceException(grant.scopeType())) {
                cap = higher(cap, grant.visibilityLevel());
                if (grant.scopeType() == ScopeType.RESOURCE) resources.add(grant.scopeRefId());
                continue;
            }
            if (!IamScopeAuthorityCeiling.organizationalGrantWithinPermission(permission, grant, tenant, evidence)) continue;
            cap = higher(cap, grant.visibilityLevel());
            switch (grant.scopeType()) {
                case TENANT -> tenantWide = true;
                case DEPARTMENT -> exact.add(grant.scopeRefId());
                case DEPARTMENT_SUBTREE -> subtrees.add(grant.scopeRefId());
                case GROUP -> groups.add(grant.scopeRefId());
                default -> { }
            }
        }
        for (ExplicitDenyRecord deny : denies) {
            switch (deny.scopeType()) {
                case TENANT -> { return deny(tenant, direct, permissionCode, resourceType, policyVersion, epoch); }
                case DEPARTMENT -> deniedDepartments.add(deny.scopeRefId());
                case DEPARTMENT_SUBTREE -> deniedSubtrees.add(deny.scopeRefId());
                case GROUP -> deniedGroups.add(deny.scopeRefId());
                case RESOURCE -> excluded.add(deny.scopeRefId());
                default -> { }
            }
        }

        // Membership identifies policy principals but is not authority by itself.
        // The IAM effective scope and explicit Resource Access grants are the only positive list-scope sources.
        if (cap == VisibilityLevel.NONE) cap = requestedVisibility == null ? VisibilityLevel.METADATA : requestedVisibility;

        ResourceListScopeQueryStrategy strategy = tenantWide ? ResourceListScopeQueryStrategy.TENANT
                : (!resources.isEmpty() && exact.isEmpty() && subtrees.isEmpty() && groups.isEmpty())
                    ? ResourceListScopeQueryStrategy.EXPLICIT_RESOURCE_SET
                    : (!subtrees.isEmpty() && resources.isEmpty() && groups.isEmpty())
                        ? ResourceListScopeQueryStrategy.DIRECT_HIERARCHY_JOIN
                        : ResourceListScopeQueryStrategy.HYBRID;
        String canonical = String.join("|", tenant, direct.principalType().name(), direct.principalId(), permissionCode,
                resourceType.name(), strategy.name(), sorted(exact), sorted(subtrees), sorted(groups), sorted(resources),
                sorted(excluded), sorted(deniedDepartments), sorted(deniedSubtrees), sorted(deniedGroups),
                policyVersion.toString(), epoch.toString());
        return new ResourceListScopeQueryPlan(tenant, direct.principalType().name(), direct.principalId(), permissionCode,
                resourceType, strategy, exact, subtrees, groups, resources, excluded, deniedDepartments, deniedSubtrees,
                deniedGroups, cap, policyVersion, epoch, sha256(canonical));
    }

    private static ResourceListScopeQueryPlan deny(String tenant, PolicyPrincipalRef principal, String permission,
            ResourceType type, PolicyVersion version, SecurityEpoch epoch) {
        return new ResourceListScopeQueryPlan(tenant, principal.principalType().name(), principal.principalId(), permission,
                type, ResourceListScopeQueryStrategy.DENY_ALL, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), VisibilityLevel.NONE, version, epoch,
                sha256("DENY|" + tenant + "|" + principal.principalId() + "|" + permission + "|" + type));
    }

    private static Set<PolicyPrincipalRef> policyPrincipals(
            com.opensocket.aievent.core.iam.security.contract.PrincipalRef principal, PrincipalScopeSnapshot scope) {
        Set<PolicyPrincipalRef> result = new LinkedHashSet<>();
        result.add(directPolicyPrincipal(principal));
        scope.departmentIds().forEach(id -> result.add(new PolicyPrincipalRef(ScopePrincipalType.DEPARTMENT, id)));
        scope.groupIds().forEach(id -> result.add(new PolicyPrincipalRef(ScopePrincipalType.GROUP, id)));
        return Set.copyOf(result);
    }

    private static PolicyPrincipalRef directPolicyPrincipal(
            com.opensocket.aievent.core.iam.security.contract.PrincipalRef principal) {
        ScopePrincipalType type = switch (principal.principalType()) {
            case USER -> ScopePrincipalType.USER;
            case DEPARTMENT -> ScopePrincipalType.DEPARTMENT;
            case GROUP -> ScopePrincipalType.GROUP;
            case SERVICE_ACCOUNT, AGENT, A2A_AGENT, INSTANCE_ROOT, SYSTEM_SERVICE -> ScopePrincipalType.SERVICE_ACCOUNT;
        };
        return new PolicyPrincipalRef(type, principal.principalId());
    }

    private static VisibilityLevel higher(VisibilityLevel left, VisibilityLevel right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
    private static String sorted(Set<String> values) { return String.join(",", new TreeSet<>(values)); }
    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
