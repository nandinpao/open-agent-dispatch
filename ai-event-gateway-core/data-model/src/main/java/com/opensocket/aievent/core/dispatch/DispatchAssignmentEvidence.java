package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only proof binding an Assignment, dispatch attempt, lease, tokens and runtime acknowledgement. */
@Getter @Setter @NoArgsConstructor
public class DispatchAssignmentEvidence {
    private String evidenceId;
    private String tenantId;
    private String dispatchRequestId;
    private String assignmentId;
    private String taskId;
    private String agentId;
    private int attemptNo;
    private String eventType;
    private String outboxStatus;
    private String workerId;
    private String claimTokenHash;
    private OffsetDateTime claimUntil;
    private String dispatchTokenHash;
    private String fencingTokenHash;
    private String runtimeSessionId;
    private String ackEvidenceId;
    private String gatewayStatus;
    private String recoveryClassification;
    private String evidenceJson;
    private OffsetDateTime occurredAt;
    private OffsetDateTime createdAt;
}
