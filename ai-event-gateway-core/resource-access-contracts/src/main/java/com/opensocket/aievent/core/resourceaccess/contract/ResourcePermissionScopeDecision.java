package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Set;

/**
 * RS1 bridge result containing every IAM organizational scope ceiling for one Permission.
 * RESOURCE exceptions remain Resource Access evidence and are deliberately not represented here.
 */
public record ResourcePermissionScopeDecision(
        boolean granted,
        String reasonCode,
        boolean tenantScoped,
        Set<String> exactDepartmentIds,
        Set<String> subtreeDepartmentRootIds,
        Set<String> groupIds,
        Set<String> matchedBindingIds,
        Set<String> matchedRoleIds) {
    public ResourcePermissionScopeDecision {
        reasonCode = reasonCode == null || reasonCode.isBlank() ? "IAM_PERMISSION_AUTHORITY_UNAVAILABLE" : reasonCode.trim();
        exactDepartmentIds = copy(exactDepartmentIds);
        subtreeDepartmentRootIds = copy(subtreeDepartmentRootIds);
        groupIds = copy(groupIds);
        matchedBindingIds = copy(matchedBindingIds);
        matchedRoleIds = copy(matchedRoleIds);
        if (!granted && (tenantScoped || !exactDepartmentIds.isEmpty() || !subtreeDepartmentRootIds.isEmpty() || !groupIds.isEmpty())) {
            throw new IllegalArgumentException("denied permission scope decision must not carry positive authority");
        }
    }

    private static Set<String> copy(Set<String> values) { return values == null ? Set.of() : Set.copyOf(values); }
}
