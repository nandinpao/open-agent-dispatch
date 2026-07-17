package com.opensocket.aievent.database.persistence.execution.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecoveryApprovalRequestPo {
    private String approvalId;
    private String status;
    private String action;
    private String targetType;
    private String targetId;
    private String dispatchRequestId;
    private String taskId;
    private String agentId;
    private String riskLevel;
    private String requestedBy;
    private String requesterPrincipal;
    private String requesterRole;
    private String requestReason;
    private String requestId;
    private String requestClientAddress;
    private String requestUserAgent;
    private String approvalReason;
    private String approvedBy;
    private String approverPrincipal;
    private String approverRole;
    private String approvalRequestId;
    private String approvalClientAddress;
    private String approvalUserAgent;
    private String rejectedBy;
    private String rejectedReason;
    private String cancelledBy;
    private String cancelledReason;
    private String executionResult;
    private String executionError;
    private OffsetDateTime expiresAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime executedAt;
    private OffsetDateTime rejectedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String payloadJson;
}
