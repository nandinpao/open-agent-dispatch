package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePermissionCatalogDraftRequest(
        @NotBlank @Size(max=128) String revisionCode,
        @Size(max=2000) String description) {}
