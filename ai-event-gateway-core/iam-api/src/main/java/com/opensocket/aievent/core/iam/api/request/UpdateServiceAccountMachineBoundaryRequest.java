package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.*;
import java.util.Set;

public record UpdateServiceAccountMachineBoundaryRequest(
        @NotBlank String responsibilityRoleId,
        String responsibilityScopeType,
        String responsibilityScopeId,
        @NotNull TokenScopeRequest restrictions,
        Set<@NotBlank String> machineScopes,
        Set<@NotBlank String> allowedSourceSystems,
        @Min(60) long credentialMaxTtlSeconds,
        @Min(1) @Max(10) int maxActiveCredentials,
        @Min(1) long expectedVersion) {}
