package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PermissionManifestEntryRequest(
        @NotBlank @Size(max=240) String entryPointId,
        @NotBlank @Size(max=32) String entryPointType,
        @NotBlank @Size(max=128) String ownerModule,
        @NotBlank @Size(max=240) String displayName,
        @Size(max=500) String routePattern,
        @Size(max=16) String httpMethod,
        @NotBlank @Size(max=32) String authorityState,
        @NotBlank @Size(max=32) String protectionMode,
        @NotBlank @Size(max=24) String coverageStatus,
        @Size(max=160) String permissionCode,
        @NotNull @Size(max=50) List<String> legacyAuthorities,
        @NotBlank @Size(max=96) String resourceType,
        @NotBlank @Size(max=200) String resourceResolverId,
        boolean scopeRequired,
        @Size(max=1000) String exemptionReason,
        @NotBlank @Size(max=1000) String sourceRef,
        @NotBlank @Pattern(regexp="^[0-9a-f]{64}$") String sourceHash,
        @NotBlank @Pattern(regexp="^[0-9a-f]{64}$") String descriptorHash) {}
