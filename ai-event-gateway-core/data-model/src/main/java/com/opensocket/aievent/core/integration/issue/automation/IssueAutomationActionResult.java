package com.opensocket.aievent.core.integration.issue.automation;

/** Durable identity of the canonical AdapterAction created or reused for one Route-B request. */
public record IssueAutomationActionResult(
        String actionId,
        String idempotencyKey,
        String status,
        boolean created) {
    public IssueAutomationActionResult {
        if (actionId == null || actionId.isBlank()) throw new IllegalArgumentException("actionId is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
    }
}
