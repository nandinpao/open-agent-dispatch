package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2ACancellationEvidence {
    private String tenantId;
    private String evidenceId;
    private String cancellationId;
    private String requestId;
    private String eventKey;
    private Integer attemptNo;
    private String assignmentId;
    private String executionAttemptId;
    private String evidenceType;
    private String evidenceReference;
    private String evidenceHash;
    private String decision;
    private String reasonCode;
    private String details;
    private OffsetDateTime occurredAt;
}
