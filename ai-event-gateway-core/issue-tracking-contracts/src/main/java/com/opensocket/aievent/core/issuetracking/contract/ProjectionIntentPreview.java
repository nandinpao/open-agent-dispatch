package com.opensocket.aievent.core.issuetracking.contract;

import java.util.List;

/** Safe preview returned before a Projection Intent is persisted or dispatched. */
public record ProjectionIntentPreview(
        ProjectionAggregateKey aggregateKey,
        String projectionId,
        ExternalIssueDocument document,
        String documentHash,
        List<String> warnings,
        boolean accepted) {
    public ProjectionIntentPreview {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
