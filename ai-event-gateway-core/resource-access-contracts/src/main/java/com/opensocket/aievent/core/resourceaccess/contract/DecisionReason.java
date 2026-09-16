package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Set;

/** Explain-safe reason. Evidence references must not disclose hidden cross-tenant resource existence. */
public record DecisionReason(String code, Category category, String safeMessage, Set<String> evidenceRefs) {
    public DecisionReason {
        code = requireText(code, "code");
        if (category == null) throw new IllegalArgumentException("category is required");
        safeMessage = safeMessage == null ? "" : safeMessage.trim();
        evidenceRefs = evidenceRefs == null ? Set.of() : Set.copyOf(evidenceRefs);
    }
    public enum Category { AUTHENTICATION, TENANT, PERMISSION, SCOPE, PARTICIPANT, OWNERSHIP, DENY, SECURITY_STATE, VISIBILITY, RUNTIME, SYSTEM }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
