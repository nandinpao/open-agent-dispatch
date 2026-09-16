package com.opensocket.aievent.core.integration.issue.projection;

import java.time.OffsetDateTime;

public record IssueRelayAttempt(
        String tenantId,
        String attemptId,
        String relayId,
        String operationCode,
        String operationSide,
        String providerType,
        String projectMappingId,
        String principalId,
        int attemptNo,
        IssueRelayOperationStatus status,
        Integer providerStatus,
        String externalIssueId,
        String externalIssueUrl,
        String responseSummary,
        String errorCode,
        boolean retryable,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String correlationId) {
}
