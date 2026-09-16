package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AResultAttempt {
    private String tenantId;
    private String attemptId;
    private String requestId;
    private String childTaskId;
    private String resultId;
    private String assignmentId;
    private String executionAttemptId;
    private Integer attemptNo;
    private String dispatchRequestId;
    private String callbackInboxId;
    private String agentId;
    private String agentSessionId;
    private A2AResultStatus resultStatus;
    private A2AResultAcceptanceDecision decision;
    private A2AResultClassification classification;
    private int resultSchemaVersion = 1;
    private String resultEvidenceHash;
    private String resultFingerprint;
    private long policyVersion;
    private String policySnapshotHash;
    private String reasonCode;
    private String reason;
    private String payloadHash;
    private String idempotencyKey;
    private String correlationId;
    private OffsetDateTime occurredAt;
    private OffsetDateTime receivedAt;
}
