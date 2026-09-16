package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

/** A canonical RBAC source that explains why a UI page or action is effective for a Person. */
public record UiAccessGrantReasonResponse(
        String permissionCode,
        String bindingId,
        String roleId,
        String roleName,
        String principalType,
        String principalId,
        String scopeType,
        String scopeId,
        Instant effectiveAt,
        Instant expiresAt,
        boolean inheritedFromGroup) { }
