package com.opensocket.aievent.core.issuetracking.contract;

import java.util.Objects;

/** Canonical relation that is independent of any provider-native relation type or identifier. */
public record ExternalIssueRelation(String relationType, String targetReference) {
    public ExternalIssueRelation {
        relationType = requireText(relationType, "relationType");
        targetReference = requireText(targetReference, "targetReference");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}
