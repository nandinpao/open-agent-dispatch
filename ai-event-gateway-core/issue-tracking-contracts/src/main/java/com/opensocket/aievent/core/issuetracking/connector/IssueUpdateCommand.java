package com.opensocket.aievent.core.issuetracking.connector;

/**
 * Controlled V1 update payload. Raw provider payloads and arbitrary custom-field maps are excluded;
 * additional business fields must be added explicitly to the contract in a future revision.
 */
public record IssueUpdateCommand(
        String issueId,
        String subject,
        String description,
        String statusId,
        String priorityId,
        String assigneeId,
        String idempotencyKey) {

    public IssueUpdateCommand {
        issueId = required(issueId, "issueId");
        subject = normalized(subject);
        description = normalized(description);
        statusId = normalized(statusId);
        priorityId = normalized(priorityId);
        assigneeId = normalized(assigneeId);
        idempotencyKey = normalized(idempotencyKey);
        if (subject == null && description == null && statusId == null && priorityId == null && assigneeId == null) {
            throw new IllegalArgumentException("At least one controlled Issue update field is required");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
