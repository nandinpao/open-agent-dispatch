package com.opensocket.aievent.core.resourceaccess.contract;

/** Validates that a create/re-scope command cannot assign a resource outside the caller's effective data scope. */
public interface ResourceScopeAssignmentGuardPort {
    boolean allows(ResourceListScopeQueryPlan plan, ResourceOwnershipCandidate candidate);
}
