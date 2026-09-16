package com.opensocket.aievent.core.issuetracking.background;
import java.util.List;
/** Resource Access authorization handle for one Issue worker iteration. Contains no credential or Secret material. */
public record IssueBackgroundExecutionAuthorization(String tenantId,String authorizationId,List<IssueBackgroundLeaseRef> leases){public IssueBackgroundExecutionAuthorization{tenantId=required(tenantId,"tenantId");authorizationId=required(authorizationId,"authorizationId");leases=leases==null?List.of():List.copyOf(leases);if(leases.isEmpty())throw new IllegalArgumentException("At least one Runtime Lease is required");}private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}}
