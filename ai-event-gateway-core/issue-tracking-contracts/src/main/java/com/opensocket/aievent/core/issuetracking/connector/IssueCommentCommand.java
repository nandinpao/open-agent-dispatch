package com.opensocket.aievent.core.issuetracking.connector;

public record IssueCommentCommand(String issueId, String comment, String idempotencyKey) {
    public IssueCommentCommand {
        issueId = required(issueId, "issueId");
        comment = required(comment, "comment");
        idempotencyKey = normalized(idempotencyKey);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
