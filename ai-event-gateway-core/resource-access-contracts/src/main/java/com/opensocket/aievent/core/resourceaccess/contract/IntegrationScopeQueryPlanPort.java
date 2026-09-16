package com.opensocket.aievent.core.resourceaccess.contract;
/** Builds server-side SQL query plans for Issue and Integration resource lists. */
public interface IntegrationScopeQueryPlanPort {
    IntegrationScopeQueryPlan build(String permissionCode, ResourceType resourceType, VisibilityLevel requestedVisibility, String purpose);
}
