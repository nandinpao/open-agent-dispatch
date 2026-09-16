package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Set;

/** Internal-only query plan. It is generated from current IAM and Resource Access evidence, never from HTTP input. */
public record TaskScopeQueryPlan(
        String tenantId,
        String principalType,
        String principalId,
        String permissionCode,
        TaskScopeQueryStrategy strategy,
        Set<String> exactDepartmentIds,
        Set<String> subtreeDepartmentRootIds,
        Set<String> groupIds,
        Set<String> explicitTaskIds,
        Set<String> excludedTaskIds,
        Set<String> deniedDepartmentIds,
        Set<String> deniedSubtreeDepartmentRootIds,
        Set<String> deniedGroupIds,
        VisibilityLevel maximumVisibility,
        PolicyVersion policyVersion,
        SecurityEpoch securityEpoch,
        String planHash) {
    public TaskScopeQueryPlan {
        tenantId = required(tenantId, "tenantId");
        principalType = required(principalType, "principalType");
        principalId = required(principalId, "principalId");
        permissionCode = required(permissionCode, "permissionCode");
        if (strategy == null) throw new IllegalArgumentException("strategy is required");
        exactDepartmentIds = copy(exactDepartmentIds);
        subtreeDepartmentRootIds = copy(subtreeDepartmentRootIds);
        groupIds = copy(groupIds);
        explicitTaskIds = copy(explicitTaskIds);
        excludedTaskIds = copy(excludedTaskIds);
        deniedDepartmentIds = copy(deniedDepartmentIds);
        deniedSubtreeDepartmentRootIds = copy(deniedSubtreeDepartmentRootIds);
        deniedGroupIds = copy(deniedGroupIds);
        maximumVisibility = maximumVisibility == null ? VisibilityLevel.NONE : maximumVisibility;
        policyVersion = policyVersion == null ? PolicyVersion.ZERO : policyVersion;
        securityEpoch = securityEpoch == null ? SecurityEpoch.ZERO : securityEpoch;
        planHash = required(planHash, "planHash");
        if (strategy == TaskScopeQueryStrategy.DENY_ALL && maximumVisibility != VisibilityLevel.NONE) {
            throw new IllegalArgumentException("DENY_ALL plan cannot grant visibility");
        }
    }
    public boolean denyAll() { return strategy == TaskScopeQueryStrategy.DENY_ALL; }
    private static Set<String> copy(Set<String> values) { return values == null ? Set.of() : Set.copyOf(values); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
