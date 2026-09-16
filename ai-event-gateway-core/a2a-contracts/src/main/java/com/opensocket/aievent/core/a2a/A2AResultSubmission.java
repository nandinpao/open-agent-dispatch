package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;

/** Evidence-bearing result command. Raw Dispatch/Fencing secrets are never persisted in A2A. */
public record A2AResultSubmission(
        String tenantId,
        String requestId,
        A2AResultStatus resultStatus,
        String resultSummary,
        String resultPayloadRef,
        List<String> evidenceRefs,
        String completedByAgentId,
        String assignmentId,
        String executionAttemptId,
        Integer attemptNo,
        String dispatchRequestId,
        String dispatchTokenHash,
        String fencingTokenHash,
        String agentSessionId,
        String callbackInboxId,
        String payloadHash,
        int resultSchemaVersion,
        String resultEvidenceHash,
        String idempotencyKey,
        String correlationId,
        OffsetDateTime occurredAt) {
    public A2AResultSubmission {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
