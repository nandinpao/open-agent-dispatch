package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpsertPermissionAliasRequest(
        @NotBlank @Size(max=160) String aliasCode,
        @NotBlank @Size(max=160) String canonicalPermissionCode,
        @NotBlank @Size(max=24) String aliasType,
        Instant validFrom,
        Instant validUntil,
        @NotBlank @Size(max=500) String reason) {}
