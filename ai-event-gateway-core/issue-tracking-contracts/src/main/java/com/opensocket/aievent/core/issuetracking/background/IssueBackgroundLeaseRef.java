package com.opensocket.aievent.core.issuetracking.background;
/** Secret-free Runtime Lease reference returned to Issue workers. */
public record IssueBackgroundLeaseRef(String leaseId,long fencingVersion){public IssueBackgroundLeaseRef{if(leaseId==null||leaseId.isBlank())throw new IllegalArgumentException("leaseId is required");if(fencingVersion<1)throw new IllegalArgumentException("fencingVersion must be positive");leaseId=leaseId.trim();}}
