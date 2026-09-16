package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.ResourcePermissionDecision;
import com.opensocket.aievent.core.resourceaccess.contract.ResourcePermissionScopeDecision;
import com.opensocket.aievent.core.resourceaccess.contract.ScopeGrantRecord;
import com.opensocket.aievent.core.resourceaccess.contract.ScopeType;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import java.util.Objects;

/**
 * Prevents Resource Access organization grants from widening the organization ceiling established by IAM Role Binding.
 * Resource-level grants remain the explicit, auditable exception mechanism.
 */
final class IamScopeAuthorityCeiling {
    private IamScopeAuthorityCeiling() { }

    static boolean organizationalGrantWithinPermission(ResourcePermissionDecision permission,
            ScopeGrantRecord grant, String tenantId, ResourceDecisionEvidenceRepository evidence) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(evidence, "evidence");
        if (permission.tenantScoped()) return organizational(grant.scopeType());
        if (permission.effectiveScopeId().isBlank()) return false;
        String ceilingType = permission.effectiveScopeType();
        String ceilingId = permission.effectiveScopeId();
        return switch (grant.scopeType()) {
            case DEPARTMENT -> departmentWithin(ceilingType, ceilingId, tenantId, grant.scopeRefId(), evidence);
            case DEPARTMENT_SUBTREE -> subtreeWithin(ceilingType, ceilingId, tenantId, grant.scopeRefId(), evidence);
            case GROUP -> "GROUP".equals(ceilingType) && ceilingId.equals(grant.scopeRefId());
            case TENANT -> false;
            default -> false;
        };
    }

    /** RS1 multi-scope authority ceiling used by list/search plan compilers. */
    static boolean organizationalGrantWithinPermission(ResourcePermissionScopeDecision permission,
            ScopeGrantRecord grant, String tenantId, ResourceDecisionEvidenceRepository evidence) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(evidence, "evidence");
        if (!permission.granted()) return false;
        if (permission.tenantScoped()) return organizational(grant.scopeType());
        return switch (grant.scopeType()) {
            case DEPARTMENT -> permission.exactDepartmentIds().contains(grant.scopeRefId())
                    || permission.subtreeDepartmentRootIds().stream()
                            .anyMatch(root -> evidence.departmentContains(tenantId, root, grant.scopeRefId()));
            case DEPARTMENT_SUBTREE -> permission.subtreeDepartmentRootIds().stream()
                    .anyMatch(root -> evidence.departmentContains(tenantId, root, grant.scopeRefId()));
            case GROUP -> permission.groupIds().contains(grant.scopeRefId());
            case TENANT -> false;
            default -> false;
        };
    }

    static boolean explicitResourceException(ScopeType type) {
        return type == ScopeType.RESOURCE || type == ScopeType.RESOURCE_TREE || type == ScopeType.TASK_CHAIN;
    }

    private static boolean organizational(ScopeType type) {
        return type == ScopeType.TENANT || type == ScopeType.DEPARTMENT
                || type == ScopeType.DEPARTMENT_SUBTREE || type == ScopeType.GROUP;
    }

    private static boolean departmentWithin(String ceilingType, String ceilingId, String tenantId,
            String departmentId, ResourceDecisionEvidenceRepository evidence) {
        if ("DEPARTMENT".equals(ceilingType)) return ceilingId.equals(departmentId);
        return "DEPARTMENT_SUBTREE".equals(ceilingType)
                && evidence.departmentContains(tenantId, ceilingId, departmentId);
    }

    private static boolean subtreeWithin(String ceilingType, String ceilingId, String tenantId,
            String subtreeRootId, ResourceDecisionEvidenceRepository evidence) {
        if (!"DEPARTMENT_SUBTREE".equals(ceilingType)) return false;
        return evidence.departmentContains(tenantId, ceilingId, subtreeRootId);
    }
}
