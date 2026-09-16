package com.opensocket.aievent.core.iam.api.response;
/** Tenant access lifecycle counters computed in the canonical Tenant context. */
public record AccessLifecycleSummaryResponse(String tenantId,long activeAssignments,long expiringAssignments,long expiredAssignments,long scheduledAssignments,long pendingApprovals,long criticalAssignments,long reviewDueAssignments,long orphanAssignments){}
