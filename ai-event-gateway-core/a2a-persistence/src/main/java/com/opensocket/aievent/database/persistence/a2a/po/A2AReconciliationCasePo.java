package com.opensocket.aievent.database.persistence.a2a.po;
import java.time.OffsetDateTime; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
@Getter @Setter @NoArgsConstructor
public class A2AReconciliationCasePo {
 private String tenantId,caseId,requestId,cancellationId,childTaskId,dispatchRequestId,resultId,handoffSnapshotId,caseType,status,authorityOwner,reasonCode,reason,diagnosisCode,evidenceSummary,evidenceSnapshotHash,recommendedAction,repairAction,requiredPermission,impactSummary,planHash,idempotencyKey,claimedBy,claimTokenHash,lastErrorCode,lastError,repairEvidenceId,resolvedBy,resolutionReason;
 private long expectedResourceVersion,version; private int attemptCount,repairAttemptNo;
 private OffsetDateTime claimUntil,heartbeatAt,nextAttemptAt,createdAt,updatedAt,resolvedAt;
}
