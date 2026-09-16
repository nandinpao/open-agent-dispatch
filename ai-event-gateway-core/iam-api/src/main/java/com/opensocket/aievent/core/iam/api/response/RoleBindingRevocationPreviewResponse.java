package com.opensocket.aievent.core.iam.api.response;

import java.util.List;

public record RoleBindingRevocationPreviewResponse(
        String bindingId,
        String roleId,
        String roleName,
        String scopeType,
        String scopeId,
        List<String> permissionsLost,
        List<String> permissionsRetained,
        List<String> warnings) {
    public RoleBindingRevocationPreviewResponse {
        permissionsLost = permissionsLost == null ? List.of() : List.copyOf(permissionsLost);
        permissionsRetained = permissionsRetained == null ? List.of() : List.copyOf(permissionsRetained);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
