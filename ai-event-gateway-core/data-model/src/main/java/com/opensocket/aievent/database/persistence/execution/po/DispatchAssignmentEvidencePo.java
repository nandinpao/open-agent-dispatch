package com.opensocket.aievent.database.persistence.execution.po;
import java.time.OffsetDateTime;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor;
@Getter @Setter @NoArgsConstructor
public class DispatchAssignmentEvidencePo {
 private String evidenceId,tenantId,dispatchRequestId,assignmentId,taskId,agentId,eventType,outboxStatus,workerId,claimTokenHash,dispatchTokenHash,fencingTokenHash,runtimeSessionId,ackEvidenceId,gatewayStatus,recoveryClassification,evidenceJson;
 private int attemptNo; private OffsetDateTime claimUntil,occurredAt,createdAt;
}
