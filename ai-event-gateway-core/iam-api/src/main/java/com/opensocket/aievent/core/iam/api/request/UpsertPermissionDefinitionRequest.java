package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public record UpsertPermissionDefinitionRequest(
        @NotBlank @Size(max=160) String permissionCode,
        @NotBlank @Size(max=128) String ownerModule,
        @NotBlank @Size(max=96) String resourceType,
        @NotBlank @Size(max=96) String actionCode,
        @NotBlank @Size(max=1000) String description,
        @NotBlank @Size(max=24) String riskLevel,
        @NotBlank @Size(max=24) String riskLane,
        @NotBlank @Size(max=24) String lifecycle,
        @NotNull @Size(min=1) Set<String> allowedScopes,
        boolean systemManaged,
        @Size(max=160) String replacementPermissionCode,
        Instant deprecatedAt,
        Instant retiredAt) {}
