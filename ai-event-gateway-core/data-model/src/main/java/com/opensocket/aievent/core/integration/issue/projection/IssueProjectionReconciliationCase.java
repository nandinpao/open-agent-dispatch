package com.opensocket.aievent.core.integration.issue.projection;

import java.time.OffsetDateTime;

public record IssueProjectionReconciliationCase(
        String tenantId,
        String caseId,
        String projectionId,
        String reasonCode,
        String evidenceReference,
        IssueProjectionReconciliationStatus status,
        OffsetDateTime nextAttemptAt,
        int attemptCount,
        String lastError,
        String resolvedBy,
        String resolutionReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt,
        String correlationId) {
}
