package com.opensocket.aievent.core.issuetracking.core;

import java.util.List;

/** Pure validation result used before a projection intent can enter a durable outbox. */
public record IssueProjectionValidation(boolean valid, List<String> violations) {
    public IssueProjectionValidation {
        violations = List.copyOf(violations == null ? List.of() : violations);
        if (valid && !violations.isEmpty()) {
            throw new IllegalArgumentException("A valid result cannot contain violations");
        }
    }

    public static IssueProjectionValidation accepted() {
        return new IssueProjectionValidation(true, List.of());
    }

    public static IssueProjectionValidation rejected(List<String> violations) {
        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("Rejected validation requires at least one violation");
        }
        return new IssueProjectionValidation(false, violations);
    }
}
