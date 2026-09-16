package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Set;
/** Current organization membership evidence for one principal. */
public record PrincipalScopeSnapshot(String tenantId,Set<String> departmentIds,Set<String> groupIds,long departmentTreeRevision){
 public PrincipalScopeSnapshot{if(tenantId==null||tenantId.isBlank())throw new IllegalArgumentException("tenantId is required");tenantId=tenantId.trim();departmentIds=departmentIds==null?Set.of():Set.copyOf(departmentIds);groupIds=groupIds==null?Set.of():Set.copyOf(groupIds);if(departmentTreeRevision<0)throw new IllegalArgumentException("departmentTreeRevision must be non-negative");}
}
