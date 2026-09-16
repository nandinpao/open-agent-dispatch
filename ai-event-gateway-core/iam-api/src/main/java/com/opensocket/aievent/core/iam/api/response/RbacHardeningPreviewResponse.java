package com.opensocket.aievent.core.iam.api.response;

import java.util.List;

public record RbacHardeningPreviewResponse(
        String operation,
        String requestHash,
        boolean critical,
        boolean approvalRequired,
        boolean selfEscalation,
        List<String> beforePermissions,
        List<String> afterPermissions,
        List<String> addedPermissions,
        List<String> removedPermissions,
        List<String> conflicts,
        List<String> warnings) {
    public RbacHardeningPreviewResponse {
        beforePermissions = beforePermissions == null ? List.of() : List.copyOf(beforePermissions);
        afterPermissions = afterPermissions == null ? List.of() : List.copyOf(afterPermissions);
        addedPermissions = addedPermissions == null ? List.of() : List.copyOf(addedPermissions);
        removedPermissions = removedPermissions == null ? List.of() : List.copyOf(removedPermissions);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
