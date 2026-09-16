package com.opensocket.aievent.core.integration.issue.projection;

import java.time.OffsetDateTime;

/** Provider projection workflow. It references Handoff snapshots but never owns them. */
public record CrossProjectIssueRelay(
        String tenantId,
        String relayId,
        String a2aRequestId,
        String rootTaskId,
        String sourceTaskId,
        String targetTaskId,
        String sourceMappingId,
        String targetMappingId,
        String sourceSnapshotId,
        String resultSnapshotId,
        String sourceIssueLinkId,
        String targetIssueLinkId,
        IssueRelayStrategy strategy,
        IssueRelayState relayState,
        Boolean nativeRelationSupported,
        String sourceBacklinkStatus,
        String targetBacklinkStatus,
        int retryCount,
        OffsetDateTime nextRetryAt,
        String lastErrorCode,
        String lastErrorMessage,
        String idempotencyKey,
        String correlationId,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
