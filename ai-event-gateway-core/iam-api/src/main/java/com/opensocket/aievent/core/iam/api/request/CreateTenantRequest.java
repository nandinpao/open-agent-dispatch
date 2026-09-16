package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creates a Tenant boundary. The internal Tenant ID is server-generated when omitted. */
public record CreateTenantRequest(
        @Size(max = 64) String tenantId,
        @NotBlank @Size(max = 64) String tenantCode,
        @NotBlank @Size(max = 200) String tenantName,
        @Size(max = 300) String legalName,
        @NotBlank String timezone,
        @NotBlank String locale,
        @NotBlank @Size(max = 64) String dataRegion) {}
