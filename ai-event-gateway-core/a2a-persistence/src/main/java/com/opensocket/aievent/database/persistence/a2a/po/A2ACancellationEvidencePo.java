package com.opensocket.aievent.database.persistence.a2a.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2ACancellationEvidencePo {
    private String tenantId,evidenceId,cancellationId,requestId,eventKey,assignmentId,
            executionAttemptId,evidenceType,evidenceReference,evidenceHash,decision,reasonCode,details;
    private Integer attemptNo;
    private OffsetDateTime occurredAt;
}
