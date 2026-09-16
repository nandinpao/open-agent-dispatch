package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Set;
public record OrganizationScopeSnapshot(String tenantId,Set<String> departmentIds,Set<String> groupIds){
    public OrganizationScopeSnapshot{if(tenantId==null||tenantId.isBlank())throw new IllegalArgumentException("tenantId is required");tenantId=tenantId.trim();departmentIds=departmentIds==null?Set.of():Set.copyOf(departmentIds);groupIds=groupIds==null?Set.of():Set.copyOf(groupIds);}
    public boolean contains(ScopeRef scope){if(scope.type()==ScopeType.TENANT)return tenantId.equals(scope.scopeId());if(scope.type()==ScopeType.DEPARTMENT||scope.type()==ScopeType.DEPARTMENT_SUBTREE)return departmentIds.contains(scope.scopeId());if(scope.type()==ScopeType.GROUP)return groupIds.contains(scope.scopeId());return false;}
}
