package com.opensocket.aievent.core.resourceaccess.contract;

/** Builds server-side scope-aware Task list query plans. */
public interface TaskScopeQueryPlanPort {
    TaskScopeQueryPlan build(String permissionCode, VisibilityLevel requestedVisibility, String purpose);
}
