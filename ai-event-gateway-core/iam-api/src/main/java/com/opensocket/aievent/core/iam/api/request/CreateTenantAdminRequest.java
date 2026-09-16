package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Creates the first daily Tenant administrator during controlled Root bootstrap.
 *
 * <p>The internal {@code userId} is optional for compatibility. When omitted,
 * null or blank, the runtime generates a stable ID from the bootstrap
 * idempotency context. Browser clients must not require operators to invent an
 * internal User ID.</p>
 */
public record CreateTenantAdminRequest(
        @NotBlank String tenantId,
        String userId,
        @NotBlank String username,
        @Email String email,
        @NotBlank String displayName) { }
