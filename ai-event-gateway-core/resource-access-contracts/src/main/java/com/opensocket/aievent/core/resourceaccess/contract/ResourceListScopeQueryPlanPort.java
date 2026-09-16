package com.opensocket.aievent.core.resourceaccess.contract;

/** Builds a server-side scope plan for list/search queries over a canonical ResourceType. */
public interface ResourceListScopeQueryPlanPort {
    ResourceListScopeQueryPlan build(String permissionCode, ResourceType resourceType,
                                     VisibilityLevel requestedVisibility, String purpose);
}
