package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;

public record CreateServiceAccountRequest(
        @Size(max=160) String serviceAccountId,
        @NotBlank String name,
        @Size(max=1000) String description,
        @NotBlank String ownerUserId,
        @NotBlank String ownerDepartmentId,
        @NotBlank String responsibilityRoleId,
        String responsibilityScopeType,
        String responsibilityScopeId,
        @NotNull TokenScopeRequest restrictions,
        Set<@NotBlank String> machineScopes,
        Set<@NotBlank String> allowedSourceSystems,
        long tokenMaxTtlSeconds,
        @Min(1) @Max(10) int maxActiveTokens,
        Long credentialMaxTtlSeconds,
        @Min(1) @Max(10) Integer maxActiveCredentials,
        @Min(1) int rateLimitPerMinute,
        @NotNull Instant nextReviewAt) {}
