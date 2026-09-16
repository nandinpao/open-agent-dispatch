package com.opensocket.aievent.database.persistence.a2a.po;

import java.time.OffsetDateTime;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
@Getter @Setter @NoArgsConstructor
public class A2AResultPo {
 private String tenantId,resultId,requestId,rootTaskId,parentTaskId,childTaskId,resultStatus,resultSummary,resultPayloadRef,evidenceRefsJson,completedByAgentId,assignmentId,executionAttemptId,dispatchRequestId,dispatchTokenHash,fencingTokenHash,agentSessionId,callbackInboxId,payloadHash,resultEvidenceHash,resultFingerprint,policySnapshotHash,acceptanceAttemptId,idempotencyKey;
 private Integer attemptNo; private int resultSchemaVersion; private long policyVersion,version;
 private OffsetDateTime receivedAt,acceptedAt,completedAt,createdAt;
}
