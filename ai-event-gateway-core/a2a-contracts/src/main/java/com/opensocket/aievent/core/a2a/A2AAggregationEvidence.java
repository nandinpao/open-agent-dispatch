package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AAggregationEvidence {
    private String tenantId;
    private String evidenceId;
    private String parentTaskId;
    private String resultId;
    private String evidenceType;
    private String computationHash;
    private String aggregateStatus;
    private String decisionReason;
    private long expectedVersion;
    private long resultingVersion;
    private int attemptNo;
    private OffsetDateTime occurredAt;
}
