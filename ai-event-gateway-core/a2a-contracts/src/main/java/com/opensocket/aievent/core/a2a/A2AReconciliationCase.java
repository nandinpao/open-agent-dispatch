package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Unified operational reconciliation aggregate introduced by Phase 2H. */
@Getter @Setter @NoArgsConstructor
public class A2AReconciliationCase {
    private String tenantId;
    private String caseId;
    private String requestId;
    private String cancellationId;
    private String childTaskId;
    private String dispatchRequestId;
    private String resultId;
    private String handoffSnapshotId;
    private String caseType;
    private A2AReconciliationStatus status = A2AReconciliationStatus.OPEN;
    private String authorityOwner;
    private String reasonCode;
    private String reason;
    private String diagnosisCode;
    private String evidenceSummary;
    private String evidenceSnapshotHash;
    private String recommendedAction;
    private String repairAction;
    private String requiredPermission;
    private String impactSummary;
    private String planHash;
    private long expectedResourceVersion;
    private int attemptCount;
    private int repairAttemptNo;
    private String idempotencyKey;
    private String claimedBy;
    private String claimTokenHash;
    private OffsetDateTime claimUntil;
    private OffsetDateTime heartbeatAt;
    private OffsetDateTime nextAttemptAt;
    private String lastErrorCode;
    private String lastError;
    private String repairEvidenceId;
    private String resolvedBy;
    private String resolutionReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime resolvedAt;
    private long version = 1L;

    public boolean active() { return status != null && status.active(); }
    public boolean claimedBy(String workerId, OffsetDateTime now) {
        return workerId != null && workerId.equals(claimedBy) && claimUntil != null && claimUntil.isAfter(now);
    }
}
