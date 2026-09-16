package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionManifestCatalogBindingRequest(
        @NotBlank @Size(max=64) String revisionId,
        @NotBlank @Size(max=128) String revisionCode) {}
