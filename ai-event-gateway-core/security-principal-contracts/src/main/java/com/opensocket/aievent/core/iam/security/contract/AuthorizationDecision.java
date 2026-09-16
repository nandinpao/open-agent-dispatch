package com.opensocket.aievent.core.iam.security.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Stable authorization output; persistence and audit adapters may enrich it outside this contract. */
public record AuthorizationDecision(
        String decisionId,
        Effect effect,
        String reasonCode,
        Set<String> matchedBindingIds,
        Set<String> matchedRoleIds,
        String effectiveScopeType,
        String effectiveScopeId,
        SecurityEpoch evaluatedEpoch,
        Instant evaluatedAt
) {
    public AuthorizationDecision {
        decisionId = requireText(decisionId, "decisionId");
        Objects.requireNonNull(effect, "effect");
        reasonCode = requireText(reasonCode, "reasonCode");
        matchedBindingIds = matchedBindingIds == null ? Set.of() : Set.copyOf(matchedBindingIds);
        matchedRoleIds = matchedRoleIds == null ? Set.of() : Set.copyOf(matchedRoleIds);
        effectiveScopeType = effectiveScopeType == null ? "" : effectiveScopeType.trim();
        effectiveScopeId = effectiveScopeId == null ? "" : effectiveScopeId.trim();
        evaluatedEpoch = evaluatedEpoch == null ? SecurityEpoch.ZERO : evaluatedEpoch;
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public enum Effect { ALLOW, DENY, SHADOW_ALLOW, SHADOW_DENY }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
