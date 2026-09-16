package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Set;

/** Candidate ownership/participant scope used to validate create and re-scope commands before persistence. */
public record ResourceOwnershipCandidate(
        String tenantId,
        String resourceId,
        Set<String> departmentIds,
        Set<String> groupIds) {
    public ResourceOwnershipCandidate {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        departmentIds = clean(departmentIds);
        groupIds = clean(groupIds);
    }
    private static Set<String> clean(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
