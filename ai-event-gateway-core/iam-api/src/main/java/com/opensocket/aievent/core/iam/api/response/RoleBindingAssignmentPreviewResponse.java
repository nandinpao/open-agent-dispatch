package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;
import java.util.List;

public record RoleBindingAssignmentPreviewResponse(
        String tenantId,
        String principalType,
        String principalId,
        String roleId,
        String roleName,
        String scopeType,
        String scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        List<String> newlyEffectivePermissions,
        List<String> alreadyEffectivePermissions,
        List<String> warnings) {
}
