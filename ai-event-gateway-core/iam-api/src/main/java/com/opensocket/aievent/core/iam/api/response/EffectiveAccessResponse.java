package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.List;

public record EffectiveAccessResponse(
        String tenantId,
        String userId,
        Instant evaluatedAt,
        List<EffectivePermissionResponse> permissions,
        List<String> conflicts) {
    public EffectiveAccessResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }
}
