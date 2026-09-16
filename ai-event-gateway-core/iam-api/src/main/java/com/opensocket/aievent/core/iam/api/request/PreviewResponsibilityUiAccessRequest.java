package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Read-only projection request for previewing UI impact before a Responsibility permission change is saved. */
public record PreviewResponsibilityUiAccessRequest(@NotNull Set<String> permissionCodes) {
    public PreviewResponsibilityUiAccessRequest {
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
    }
}
