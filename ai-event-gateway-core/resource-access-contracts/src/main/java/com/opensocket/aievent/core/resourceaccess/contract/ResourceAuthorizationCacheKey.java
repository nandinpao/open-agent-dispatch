package com.opensocket.aievent.core.resourceaccess.contract;
/** Versioned cache namespace. Epoch changes produce a different key; stale allows are never reused. */
public record ResourceAuthorizationCacheKey(String tenantId,String principalType,String principalId,String permissionCode,ResourceType resourceType,String resourceId,VisibilityLevel requestedVisibility,PolicyVersion policyVersion,SecurityEpoch securityEpoch){
 public ResourceAuthorizationCacheKey{tenantId=req(tenantId,"tenantId");principalType=req(principalType,"principalType");principalId=req(principalId,"principalId");permissionCode=req(permissionCode,"permissionCode");if(resourceType==null)throw new IllegalArgumentException("resourceType is required");resourceId=req(resourceId,"resourceId");if(requestedVisibility==null)throw new IllegalArgumentException("requestedVisibility is required");policyVersion=policyVersion==null?PolicyVersion.ZERO:policyVersion;securityEpoch=securityEpoch==null?SecurityEpoch.ZERO:securityEpoch;}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
