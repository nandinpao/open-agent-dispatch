package com.opensocket.aievent.core.issuetracking.connector;

/**
 * Controlled create payload. Provider destination (connection/project/tracker) is resolved by
 * OpenDispatch configuration and is intentionally absent from the command.
 */
public record IssueCreateCommand(
        String subject,
        String description,
        String priorityId,
        String idempotencyKey,
        java.util.Map<String, Object> providerFields) {

    public IssueCreateCommand {
        subject = required(subject, "subject");
        description = normalized(description);
        priorityId = normalized(priorityId);
        idempotencyKey = normalized(idempotencyKey);
        providerFields = providerFields == null ? java.util.Map.of() : java.util.Map.copyOf(providerFields);
    }

    /** Compatibility constructor for callers that do not materialize provider fields. */
    public IssueCreateCommand(String subject, String description, String priorityId, String idempotencyKey) {
        this(subject, description, priorityId, idempotencyKey, java.util.Map.of());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
