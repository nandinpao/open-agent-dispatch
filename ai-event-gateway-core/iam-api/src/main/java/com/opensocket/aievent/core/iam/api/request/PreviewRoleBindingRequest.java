package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Read-only preview input. The actual write still uses BindRoleRequest plus Idempotency-Key and X-Audit-Reason. */
public record PreviewRoleBindingRequest(
        @NotNull PrincipalRef.PrincipalType principalType,
        @NotBlank String principalId,
        @NotBlank String roleId,
        @NotBlank String scopeType,
        @NotBlank String scopeId,
        Instant effectiveAt,
        Instant expiresAt) {
}
