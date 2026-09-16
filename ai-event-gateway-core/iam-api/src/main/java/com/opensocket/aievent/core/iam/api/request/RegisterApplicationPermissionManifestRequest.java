package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterApplicationPermissionManifestRequest(
        int schemaVersion,
        @NotBlank @Size(max=128) String applicationId,
        @NotBlank @Size(max=64) String environment,
        @NotBlank @Size(max=128) String buildVersion,
        @NotBlank @Size(max=160) String manifestRevision,
        @NotBlank @Size(max=160) String sourceInventoryRevision,
        @NotBlank @Pattern(regexp="^sha256:[0-9a-f]{64}$") String contentHash,
        @NotNull @Valid PermissionManifestCatalogBindingRequest catalogBinding,
        @NotNull @Valid PermissionManifestStatisticsRequest statistics,
        @Valid @NotNull @Size(min=1,max=2000) List<PermissionManifestEntryRequest> entries) {}
