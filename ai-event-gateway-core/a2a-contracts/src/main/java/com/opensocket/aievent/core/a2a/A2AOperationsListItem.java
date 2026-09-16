package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;

/** Stable list projection for operator triage. */
public record A2AOperationsListItem(
        String requestId, String rootTaskId, String sourceTaskId, String childTaskId,
        String sourceDomainId, String targetDomainId, String requestedTaskType,
        String requestStatus, String operationalStage, String childTaskStatus, String dispatchStatus, String runtimeStatus,
        String resultStatus, String cancellationStatus, String aggregationStatus,
        String blockerCode, String blockerMessage, String recommendedAction,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, long version) {
}
