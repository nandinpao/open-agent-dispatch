package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Server-side create/re-scope guard. Every non-null Department/Group participant must remain inside effective scope. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public final class DefaultResourceScopeAssignmentGuard implements ResourceScopeAssignmentGuardPort {
    private final ResourceDecisionEvidenceRepository evidence;
    public DefaultResourceScopeAssignmentGuard(ResourceDecisionEvidenceRepository evidence) {
        this.evidence = Objects.requireNonNull(evidence, "evidence");
    }

    @Override
    public boolean allows(ResourceListScopeQueryPlan plan, ResourceOwnershipCandidate candidate) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(candidate, "candidate");
        if (plan.denyAll() || !plan.tenantId().equals(candidate.tenantId())) return false;
        if (plan.excludedResourceIds().contains(candidate.resourceId())) return false;
        if (plan.tenantWide()) return true;
        if (plan.explicitResourceIds().contains(candidate.resourceId())
                && candidate.departmentIds().isEmpty() && candidate.groupIds().isEmpty()) return true;
        if (candidate.departmentIds().isEmpty() && candidate.groupIds().isEmpty()) return false;
        for (String departmentId : candidate.departmentIds()) {
            if (deniedDepartment(plan, candidate.tenantId(), departmentId) || !allowedDepartment(plan, candidate.tenantId(), departmentId)) return false;
        }
        for (String groupId : candidate.groupIds()) {
            if (plan.deniedGroupIds().contains(groupId) || !plan.groupIds().contains(groupId)) return false;
        }
        return true;
    }

    private boolean allowedDepartment(ResourceListScopeQueryPlan plan, String tenantId, String departmentId) {
        if (plan.exactDepartmentIds().contains(departmentId)) return true;
        return plan.subtreeDepartmentRootIds().stream().anyMatch(root -> evidence.departmentContains(tenantId, root, departmentId));
    }
    private boolean deniedDepartment(ResourceListScopeQueryPlan plan, String tenantId, String departmentId) {
        if (plan.deniedDepartmentIds().contains(departmentId)) return true;
        return plan.deniedSubtreeDepartmentRootIds().stream().anyMatch(root -> evidence.departmentContains(tenantId, root, departmentId));
    }
}
