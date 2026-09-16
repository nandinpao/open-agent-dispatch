package com.opensocket.aievent.core.integration.identity;

import java.util.Set;

/** Provider-neutral SQL scope supplied by the Resource Access bridge; never accepted from HTTP input. */
public record IntegrationResourceScope(String tenantId,String principalType,String principalId,String permissionCode,String resourceType,
 boolean denyAll,boolean tenantWide,Set<String> exactDepartmentIds,Set<String> subtreeDepartmentRootIds,Set<String> groupIds,
 Set<String> explicitResourceIds,Set<String> excludedResourceIds,Set<String> deniedDepartmentIds,
 Set<String> deniedSubtreeDepartmentRootIds,Set<String> deniedGroupIds,String maximumVisibility,String planHash) {
 public IntegrationResourceScope {tenantId=req(tenantId,"tenantId");principalType=req(principalType,"principalType");principalId=req(principalId,"principalId");
 permissionCode=req(permissionCode,"permissionCode");resourceType=req(resourceType,"resourceType");exactDepartmentIds=copy(exactDepartmentIds);
 subtreeDepartmentRootIds=copy(subtreeDepartmentRootIds);groupIds=copy(groupIds);explicitResourceIds=copy(explicitResourceIds);
 excludedResourceIds=copy(excludedResourceIds);deniedDepartmentIds=copy(deniedDepartmentIds);
 deniedSubtreeDepartmentRootIds=copy(deniedSubtreeDepartmentRootIds);deniedGroupIds=copy(deniedGroupIds);
 maximumVisibility=req(maximumVisibility,"maximumVisibility");planHash=req(planHash,"planHash");}
 private static Set<String> copy(Set<String> v){return v==null?Set.of():Set.copyOf(v);} private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}
