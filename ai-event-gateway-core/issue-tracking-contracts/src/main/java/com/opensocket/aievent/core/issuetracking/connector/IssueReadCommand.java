package com.opensocket.aievent.core.issuetracking.connector;

public record IssueReadCommand(String issueId) {
    public IssueReadCommand {
        issueId = required(issueId, "issueId");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
