package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AResult {
    private String tenantId;
    private String resultId;
    private String requestId;
    private String rootTaskId;
    private String parentTaskId;
    private String childTaskId;
    private A2AResultStatus resultStatus;
    private String resultSummary;
    private String resultPayloadRef;
    private List<String> evidenceRefs = List.of();
    private String completedByAgentId;
    private String assignmentId;
    private String executionAttemptId;
    private Integer attemptNo;
    private String dispatchRequestId;
    private String dispatchTokenHash;
    private String fencingTokenHash;
    private String agentSessionId;
    private String callbackInboxId;
    private String payloadHash;
    private int resultSchemaVersion = 1;
    private String resultEvidenceHash;
    private String resultFingerprint;
    private long policyVersion;
    private String policySnapshotHash;
    private String acceptanceAttemptId;
    private OffsetDateTime receivedAt;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime completedAt;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
    private long version = 1L;
}
