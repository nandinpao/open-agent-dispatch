package com.opensocket.aievent.core.iam.api.response;

import java.time.Instant;

public record EffectiveAccessSourceResponse(
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
