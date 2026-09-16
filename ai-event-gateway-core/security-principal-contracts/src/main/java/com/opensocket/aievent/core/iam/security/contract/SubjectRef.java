package com.opensocket.aievent.core.iam.security.contract;

import java.util.Objects;

/** Stable authenticated subject reference; contains no domain entity. */
public record SubjectRef(IdentityType identityType, String subjectId) {
    public SubjectRef {
        Objects.requireNonNull(identityType, "identityType");
        subjectId = requireText(subjectId, "subjectId");
    }

    public enum IdentityType {
        INSTANCE_ROOT,
        HUMAN_USER,
        SERVICE_ACCOUNT,
        AGENT,
        A2A_AGENT,
        SYSTEM_SERVICE
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
