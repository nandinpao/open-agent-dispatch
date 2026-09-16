package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Set;

/**
 * Canonical SQL-consumable data-authorization plan.
 *
 * <p>The plan is server generated from current IAM role-binding scope plus Resource Access grants/denies.
 * HTTP callers can never supply these scope sets directly.</p>
 */
public record ResourceListScopeQueryPlan(
        String tenantId,
        String principalType,
        String principalId,
        String permissionCode,
        ResourceType resourceType,
        ResourceListScopeQueryStrategy strategy,
        Set<String> exactDepartmentIds,
        Set<String> subtreeDepartmentRootIds,
        Set<String> groupIds,
        Set<String> explicitResourceIds,
        Set<String> excludedResourceIds,
        Set<String> deniedDepartmentIds,
        Set<String> deniedSubtreeDepartmentRootIds,
        Set<String> deniedGroupIds,
        VisibilityLevel maximumVisibility,
        PolicyVersion policyVersion,
        SecurityEpoch securityEpoch,
        String planHash) {
    public ResourceListScopeQueryPlan {
        tenantId = required(tenantId, "tenantId");
        principalType = required(principalType, "principalType");
        principalId = required(principalId, "principalId");
        permissionCode = required(permissionCode, "permissionCode");
        if (resourceType == null) throw new IllegalArgumentException("resourceType is required");
        if (strategy == null) throw new IllegalArgumentException("strategy is required");
        exactDepartmentIds = copy(exactDepartmentIds);
        subtreeDepartmentRootIds = copy(subtreeDepartmentRootIds);
        groupIds = copy(groupIds);
        explicitResourceIds = copy(explicitResourceIds);
        excludedResourceIds = copy(excludedResourceIds);
        deniedDepartmentIds = copy(deniedDepartmentIds);
        deniedSubtreeDepartmentRootIds = copy(deniedSubtreeDepartmentRootIds);
        deniedGroupIds = copy(deniedGroupIds);
        maximumVisibility = maximumVisibility == null ? VisibilityLevel.NONE : maximumVisibility;
        policyVersion = policyVersion == null ? PolicyVersion.ZERO : policyVersion;
        securityEpoch = securityEpoch == null ? SecurityEpoch.ZERO : securityEpoch;
        planHash = required(planHash, "planHash");
        if (strategy == ResourceListScopeQueryStrategy.DENY_ALL && maximumVisibility != VisibilityLevel.NONE) {
            throw new IllegalArgumentException("DENY_ALL plan cannot grant visibility");
        }
    }

    public boolean denyAll() { return strategy == ResourceListScopeQueryStrategy.DENY_ALL; }
    public boolean tenantWide() { return strategy == ResourceListScopeQueryStrategy.TENANT; }

    private static Set<String> copy(Set<String> values) { return values == null ? Set.of() : Set.copyOf(values); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
