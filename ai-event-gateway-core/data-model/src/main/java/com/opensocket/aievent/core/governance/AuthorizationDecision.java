package com.opensocket.aievent.core.governance;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Legacy API mutation admission evidence.
 *
 * <p>This record proves that the HTTP mutation contract was accepted and audited. It is not the
 * IAM or Resource Access authorization authority and must never be used to grant resource access.
 * The historical class/table/header names are retained for compatibility until a governed migration.
 */
public record AuthorizationDecision(
        String tenantId,
        String decisionId,
        String permissionPoint,
        String resourceType,
        String resourceId,
        String actorType,
        String actorId,
        String decision,
        String reasonCode,
        List<String> evaluatedScopes,
        String correlationId,
        OffsetDateTime decidedAt) {
    public AuthorizationDecision {
        evaluatedScopes = evaluatedScopes == null ? List.of() : List.copyOf(evaluatedScopes);
    }
}
