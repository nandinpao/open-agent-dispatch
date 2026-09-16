package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Objects;
import java.util.Set;

/** One effective IAM permission grant preserving the Role Binding scope ceiling that produced it. */
public record EffectivePermissionScopeGrant(
        ScopeType scopeType,
        String scopeId,
        Set<String> matchedBindingIds,
        Set<String> matchedRoleIds) {
    public EffectivePermissionScopeGrant {
        Objects.requireNonNull(scopeType, "scopeType");
        scopeId = scopeId == null ? "" : scopeId.trim();
        if (scopeType != ScopeType.INSTANCE && scopeId.isEmpty()) {
            throw new IllegalArgumentException("scopeId is required for tenant-owned effective scopes");
        }
        if (scopeType == ScopeType.INSTANCE && scopeId.isEmpty()) scopeId = "INSTANCE";
        matchedBindingIds = matchedBindingIds == null ? Set.of() : Set.copyOf(matchedBindingIds);
        matchedRoleIds = matchedRoleIds == null ? Set.of() : Set.copyOf(matchedRoleIds);
    }
}
