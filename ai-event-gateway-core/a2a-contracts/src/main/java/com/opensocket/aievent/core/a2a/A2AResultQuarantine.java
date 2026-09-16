package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AResultQuarantine {
    private String tenantId;
    private String quarantineId;
    private String attemptId;
    private String requestId;
    private String childTaskId;
    private A2AResultClassification classification;
    private String reasonCode;
    private String reason;
    private String payloadHash;
    private String callbackInboxId;
    private String assignmentId;
    private String executionAttemptId;
    private String status;
    private String resolvedBy;
    private String resolutionReason;
    private OffsetDateTime quarantinedAt;
    private OffsetDateTime resolvedAt;
}
