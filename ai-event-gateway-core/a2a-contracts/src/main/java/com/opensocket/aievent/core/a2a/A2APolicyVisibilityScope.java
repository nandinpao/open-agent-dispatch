package com.opensocket.aievent.core.a2a;

import java.util.Set;

/** Neutral A2A persistence scope; produced only by the server-side Resource Access layer. */
public record A2APolicyVisibilityScope(
        boolean denyAll,
        boolean tenantWide,
        Set<String> exactDepartmentIds,
        Set<String> subtreeDepartmentRootIds,
        Set<String> groupIds,
        Set<String> explicitPolicyIds,
        Set<String> excludedPolicyIds,
        Set<String> deniedDepartmentIds,
        Set<String> deniedSubtreeDepartmentRootIds,
        Set<String> deniedGroupIds) {
    public A2APolicyVisibilityScope {
        exactDepartmentIds=copy(exactDepartmentIds);subtreeDepartmentRootIds=copy(subtreeDepartmentRootIds);groupIds=copy(groupIds);
        explicitPolicyIds=copy(explicitPolicyIds);excludedPolicyIds=copy(excludedPolicyIds);deniedDepartmentIds=copy(deniedDepartmentIds);
        deniedSubtreeDepartmentRootIds=copy(deniedSubtreeDepartmentRootIds);deniedGroupIds=copy(deniedGroupIds);
    }
    private static Set<String> copy(Set<String> value){return value==null?Set.of():Set.copyOf(value);}
}
