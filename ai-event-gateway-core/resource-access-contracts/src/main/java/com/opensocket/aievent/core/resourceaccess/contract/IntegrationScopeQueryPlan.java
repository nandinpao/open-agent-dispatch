package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Set;

/** Internal-only Issue/Integration query plan generated from current IAM and Resource Access evidence. */
public record IntegrationScopeQueryPlan(
        String tenantId, String principalType, String principalId, String permissionCode, ResourceType resourceType,
        IntegrationScopeQueryStrategy strategy, Set<String> exactDepartmentIds, Set<String> subtreeDepartmentRootIds,
        Set<String> groupIds, Set<String> explicitResourceIds, Set<String> excludedResourceIds,
        Set<String> deniedDepartmentIds, Set<String> deniedSubtreeDepartmentRootIds, Set<String> deniedGroupIds,
        VisibilityLevel maximumVisibility, PolicyVersion policyVersion, SecurityEpoch securityEpoch, String planHash) {
    public IntegrationScopeQueryPlan {
        tenantId=required(tenantId,"tenantId"); principalType=required(principalType,"principalType");
        principalId=required(principalId,"principalId"); permissionCode=required(permissionCode,"permissionCode");
        if(resourceType==null) throw new IllegalArgumentException("resourceType is required");
        if(strategy==null) throw new IllegalArgumentException("strategy is required");
        exactDepartmentIds=copy(exactDepartmentIds); subtreeDepartmentRootIds=copy(subtreeDepartmentRootIds);
        groupIds=copy(groupIds); explicitResourceIds=copy(explicitResourceIds); excludedResourceIds=copy(excludedResourceIds);
        deniedDepartmentIds=copy(deniedDepartmentIds); deniedSubtreeDepartmentRootIds=copy(deniedSubtreeDepartmentRootIds);
        deniedGroupIds=copy(deniedGroupIds); maximumVisibility=maximumVisibility==null?VisibilityLevel.NONE:maximumVisibility;
        policyVersion=policyVersion==null?PolicyVersion.ZERO:policyVersion; securityEpoch=securityEpoch==null?SecurityEpoch.ZERO:securityEpoch;
        planHash=required(planHash,"planHash");
        if(strategy==IntegrationScopeQueryStrategy.DENY_ALL && maximumVisibility!=VisibilityLevel.NONE)
            throw new IllegalArgumentException("DENY_ALL plan cannot grant visibility");
    }
    public boolean denyAll(){return strategy==IntegrationScopeQueryStrategy.DENY_ALL;}
    public boolean tenantWide(){return strategy==IntegrationScopeQueryStrategy.TENANT;}
    private static Set<String> copy(Set<String> v){return v==null?Set.of():Set.copyOf(v);}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
