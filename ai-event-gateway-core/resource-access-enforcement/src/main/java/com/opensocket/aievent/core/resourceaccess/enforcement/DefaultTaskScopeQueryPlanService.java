package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Builds SQL-consumable Task scope plans without loading all Tasks into Java. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = {"enabled", "task-enabled"}, havingValue = "true")
public final class DefaultTaskScopeQueryPlanService implements TaskScopeQueryPlanPort {
    private final ResourceDecisionEvidenceRepository evidence;
    private final ResourcePermissionAuthorityPort permissions;
    private final ResourceEnforcementContextPort contexts;
    private final TaskScopeQueryAuditPort audit;
    private final MaterializedScopeSnapshotPort snapshots;
    private final ResourceScopeShareRepositoryPort shares;

    public DefaultTaskScopeQueryPlanService(ResourceDecisionEvidenceRepository evidence,
            ResourcePermissionAuthorityPort permissions, ResourceEnforcementContextPort contexts,
            ObjectProvider<TaskScopeQueryAuditPort> auditProvider,
            ObjectProvider<MaterializedScopeSnapshotPort> snapshotProvider,
            ResourceScopeShareRepositoryPort shares) {
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.audit = auditProvider == null ? null : auditProvider.getIfAvailable();
        this.snapshots = snapshotProvider == null ? null : snapshotProvider.getIfAvailable();
        this.shares = Objects.requireNonNull(shares, "shares");
    }

    @Override
    public TaskScopeQueryPlan build(String permissionCode, VisibilityLevel requestedVisibility, String purpose) {
        ResourceEnforcementContext runtime = contexts.current();
        var auth = runtime.authentication();
        PolicyPrincipalRef directPrincipal = directPolicyPrincipal(auth.principal());
        String tenant = auth.activeTenant().tenantId();
        Instant at = runtime.requestedAt();
        PrincipalScopeSnapshot principalScope = evidence.resolvePrincipalScope(tenant, auth.principal(), at);
        ResourceRef synthetic = new ResourceRef(tenant, ResourceType.TASK, "__TASK_LIST_SCOPE__");
        AuthorizationRequest permissionRequest = new AuthorizationRequest(
                auth.principal(), auth, auth.activeTenant(),
                new ResourceAction(required(permissionCode, "permissionCode"), ResourceAction.ActionKind.READ, false),
                synthetic, requestedVisibility == null ? VisibilityLevel.METADATA : requestedVisibility,
                RequestChannel.INTERNAL_PORT, required(purpose, "purpose"), OperationPhase.START, "",
                runtime.correlationId(), SecurityEpoch.ZERO, Map.of("queryPlan", "TASK_LIST"));
        ResourcePermissionScopeDecision permission = permissions.resolveEffectiveScopes(permissionRequest, principalScope);
        PolicyVersion policyVersion = evidence.currentPolicyVersion(tenant);
        SecurityEpoch epoch = evidence.currentSecurityEpoch(tenant, auth.principal(), synthetic);
        if (!permission.granted()) return deny(tenant, auth, permissionCode, policyVersion, epoch);
        if (snapshots != null) {
            Optional<MaterializedScopeSnapshot> active = snapshots.findActive(
                    tenant, ScopeSnapshotKind.TASK_LIST, directPrincipal.principalType().name(),
                    auth.principal().principalId(), permissionCode, ResourceType.TASK, policyVersion, epoch, at);
            if (active.isPresent()) {
                TaskScopeQueryPlan materialized = toTaskPlan(active.get());
                if (audit != null) audit.recordPlan(materialized, purpose + ":MATERIALIZED", at);
                return materialized;
            }
        }

        Set<PolicyPrincipalRef> principals = policyPrincipals(auth.principal(), principalScope);
        List<ScopeGrantRecord> grants = evidence.findEffectiveScopeGrants(
                tenant, principals, permissionCode, ResourceType.TASK, at);
        List<ExplicitDenyRecord> denies = evidence.findEffectiveExplicitDenies(
                tenant, principals, permissionCode, ResourceType.TASK, at);
        Set<String> sharedTaskIds = shares.findEffectiveSharedResourceIds(tenant, ResourceType.TASK, permission, at);

        Set<String> exactDepartments = new LinkedHashSet<>();
        Set<String> subtreeRoots = new LinkedHashSet<>();
        Set<String> groups = new LinkedHashSet<>();
        Set<String> resources = new LinkedHashSet<>(sharedTaskIds);
        Set<String> excludedResources = new LinkedHashSet<>();
        Set<String> deniedDepartments = new LinkedHashSet<>();
        Set<String> deniedSubtrees = new LinkedHashSet<>();
        Set<String> deniedGroups = new LinkedHashSet<>();
        VisibilityLevel cap = VisibilityLevel.NONE;
        boolean tenantWide = permission.tenantScoped();
        exactDepartments.addAll(permission.exactDepartmentIds());
        subtreeRoots.addAll(permission.subtreeDepartmentRootIds());
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
                case DEPARTMENT -> exactDepartments.add(grant.scopeRefId());
                case DEPARTMENT_SUBTREE -> subtreeRoots.add(grant.scopeRefId());
                case GROUP -> groups.add(grant.scopeRefId());
                default -> { /* OWNER/PARTICIPANT do not widen IAM organization authority. */ }
            }
        }
        for (ExplicitDenyRecord deny : denies) {
            switch (deny.scopeType()) {
                case TENANT -> { return deny(tenant, auth, permissionCode, policyVersion, epoch); }
                case DEPARTMENT -> deniedDepartments.add(deny.scopeRefId());
                case DEPARTMENT_SUBTREE -> deniedSubtrees.add(deny.scopeRefId());
                case GROUP -> deniedGroups.add(deny.scopeRefId());
                case RESOURCE -> excludedResources.add(deny.scopeRefId());
                default -> { /* evaluated per row/detail by the formal guard. */ }
            }
        }
        // Do not broaden an IAM Role Binding scope merely because the principal belongs to another Department/Group.
        // Membership is used above only to resolve explicit Resource Access grants/denies for those principals.
        if (cap == VisibilityLevel.NONE) cap = requestedVisibility == null ? VisibilityLevel.METADATA : requestedVisibility;
        TaskScopeQueryStrategy strategy = tenantWide ? TaskScopeQueryStrategy.TENANT
                : (!resources.isEmpty() && exactDepartments.isEmpty() && subtreeRoots.isEmpty() && groups.isEmpty())
                    ? TaskScopeQueryStrategy.EXPLICIT_RESOURCE_SET
                    : (!subtreeRoots.isEmpty() && resources.isEmpty() && groups.isEmpty())
                        ? TaskScopeQueryStrategy.DIRECT_HIERARCHY_JOIN
                        : TaskScopeQueryStrategy.HYBRID;
        String canonical = String.join("|", tenant, directPrincipal.principalType().name(), auth.principal().principalId(),
                permissionCode, strategy.name(), sorted(exactDepartments), sorted(subtreeRoots), sorted(groups),
                sorted(resources), sorted(excludedResources), sorted(deniedDepartments), sorted(deniedSubtrees),
                sorted(deniedGroups), policyVersion.toString(), epoch.toString());
        // Deny sets are encoded in planHash and applied by SQL through negative scope evidence tables.
        TaskScopeQueryPlan plan = new TaskScopeQueryPlan(tenant, directPrincipal.principalType().name(), auth.principal().principalId(),
                permissionCode, strategy, exactDepartments, subtreeRoots, groups, resources, excludedResources,
                deniedDepartments, deniedSubtrees, deniedGroups, cap, policyVersion, epoch, sha256(canonical));
        if (snapshots != null) {
            snapshots.savePrepared(toSnapshot(plan, at), runtime.correlationId());
        }
        if (audit != null) audit.recordPlan(plan, purpose, at);
        return plan;
    }

    private static MaterializedScopeSnapshot toSnapshot(TaskScopeQueryPlan plan, Instant at) {
        return new MaterializedScopeSnapshot(
                "mss-" + UUID.randomUUID(), plan.tenantId(), ScopeSnapshotKind.TASK_LIST, plan.principalType(),
                plan.principalId(), plan.permissionCode(), ResourceType.TASK, plan.strategy().name(),
                plan.exactDepartmentIds(), plan.subtreeDepartmentRootIds(), plan.groupIds(), plan.explicitTaskIds(),
                plan.excludedTaskIds(), plan.deniedDepartmentIds(), plan.deniedSubtreeDepartmentRootIds(),
                plan.deniedGroupIds(), plan.maximumVisibility(), plan.policyVersion(), plan.securityEpoch(),
                plan.securityEpoch().departmentTreeRevision(), plan.planHash(), MaterializedScopeSnapshotStatus.PREPARED,
                at, null, null, 0);
    }

    private static TaskScopeQueryPlan toTaskPlan(MaterializedScopeSnapshot snapshot) {
        return new TaskScopeQueryPlan(snapshot.tenantId(), snapshot.principalType(), snapshot.principalId(),
                snapshot.permissionCode(), TaskScopeQueryStrategy.valueOf(snapshot.strategy()), snapshot.exactDepartmentIds(),
                snapshot.subtreeDepartmentRootIds(), snapshot.groupIds(), snapshot.explicitResourceIds(),
                snapshot.excludedResourceIds(), snapshot.deniedDepartmentIds(), snapshot.deniedSubtreeDepartmentRootIds(),
                snapshot.deniedGroupIds(), snapshot.maximumVisibility(), snapshot.policyVersion(), snapshot.securityEpoch(),
                snapshot.planHash());
    }

    private static TaskScopeQueryPlan deny(String tenantId,
            com.opensocket.aievent.core.iam.security.contract.AuthenticationContext auth, String permission,
            PolicyVersion version, SecurityEpoch epoch) {
        return new TaskScopeQueryPlan(tenantId, directPolicyPrincipal(auth.principal()).principalType().name(), auth.principal().principalId(),
                permission, TaskScopeQueryStrategy.DENY_ALL, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), VisibilityLevel.NONE, version, epoch, sha256("DENY|" + tenantId + "|" + auth.principal().principalId() + "|" + permission));
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
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
