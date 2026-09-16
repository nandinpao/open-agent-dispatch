package com.opensocket.aievent.core.iam.api.response;

import java.util.Set;

/** Active organization memberships used only to constrain scoped user-read projections. */
public record UserOrganizationScopeResponse(
        String tenantId,
        String userId,
        Set<String> departmentIds,
        Set<String> groupIds) {
    public UserOrganizationScopeResponse {
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        groupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
    }
}
