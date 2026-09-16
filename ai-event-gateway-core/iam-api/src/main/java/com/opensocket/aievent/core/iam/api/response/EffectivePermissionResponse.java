package com.opensocket.aievent.core.iam.api.response;

import java.util.List;

public record EffectivePermissionResponse(
        String permissionCode,
        List<EffectiveAccessSourceResponse> sources,
        List<String> observations) {
    public EffectivePermissionResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        observations = observations == null ? List.of() : List.copyOf(observations);
    }
}
