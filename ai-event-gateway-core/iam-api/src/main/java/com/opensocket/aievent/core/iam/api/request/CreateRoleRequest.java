package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Role IDs are server-generated when omitted; operators manage names and permissions, not database identifiers. */
public record CreateRoleRequest(
        String roleId,
        @NotBlank String roleCode,
        @NotBlank String roleName,
        @Size(max = 1000) String description) {
    public CreateRoleRequest {
        roleId = normalizeOptional(roleId);
        roleCode = roleCode == null ? null : roleCode.trim();
        roleName = roleName == null ? null : roleName.trim();
    }
    public String authorizationTarget() { return roleId == null ? "SERVER_GENERATED_ROLE" : roleId; }
    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
