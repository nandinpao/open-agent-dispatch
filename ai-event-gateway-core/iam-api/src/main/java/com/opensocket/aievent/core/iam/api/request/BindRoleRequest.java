package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Binding IDs are server-generated when omitted; the UI selects Principal, Role and Scope. */
public record BindRoleRequest(
        String bindingId,
        @NotNull PrincipalRef.PrincipalType principalType,
        @NotBlank String principalId,
        @NotBlank String roleId,
        @NotBlank String scopeType,
        @NotBlank String scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        String approvalId) {
    public BindRoleRequest {
        bindingId = normalizeOptional(bindingId);
        principalId = principalId == null ? null : principalId.trim();
        roleId = roleId == null ? null : roleId.trim();
        scopeType = scopeType == null ? null : scopeType.trim();
        scopeId = scopeId == null ? null : scopeId.trim();
        approvalId = normalizeOptional(approvalId);
    }
    public String authorizationTarget() { return bindingId == null ? "SERVER_GENERATED_ROLE_BINDING" : bindingId; }
    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
