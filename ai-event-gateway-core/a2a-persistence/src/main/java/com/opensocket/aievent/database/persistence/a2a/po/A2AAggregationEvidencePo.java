package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class A2AAggregationEvidencePo {
 private String tenantId,evidenceId,parentTaskId,resultId,evidenceType,computationHash,aggregateStatus,decisionReason;
 private long expectedVersion,resultingVersion; private int attemptNo; private OffsetDateTime occurredAt;
}
