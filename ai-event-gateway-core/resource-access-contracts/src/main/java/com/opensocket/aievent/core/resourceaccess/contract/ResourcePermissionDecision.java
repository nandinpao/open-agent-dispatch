package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Set;
/** Existing IAM RBAC permission evidence; it does not decide Resource Scope. */
public record ResourcePermissionDecision(boolean granted,String reasonCode,Set<String> matchedBindingIds,Set<String> matchedRoleIds,String effectiveScopeType,String effectiveScopeId){
 public ResourcePermissionDecision{reasonCode=required(reasonCode,"reasonCode");matchedBindingIds=matchedBindingIds==null?Set.of():Set.copyOf(matchedBindingIds);matchedRoleIds=matchedRoleIds==null?Set.of():Set.copyOf(matchedRoleIds);effectiveScopeType=effectiveScopeType==null?"":effectiveScopeType.trim();effectiveScopeId=effectiveScopeId==null?"":effectiveScopeId.trim();}
 public boolean tenantScoped(){return granted&&"TENANT".equals(effectiveScopeType);}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
