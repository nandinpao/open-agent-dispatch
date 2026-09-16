package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.rbac.domain.RbacCriticalChangeApproval;
import java.time.Instant;

public record RbacCriticalApprovalResponse(String approvalId,String tenantId,String operation,String requestHash,
        String requesterId,String targetType,String targetId,String status,String approverId,String decisionReason,
        Instant requestedAt,Instant expiresAt,Instant decidedAt,Instant consumedAt,long version) {
    public static RbacCriticalApprovalResponse from(RbacCriticalChangeApproval a){return new RbacCriticalApprovalResponse(
            a.approvalId(),a.tenantId(),a.operation().name(),a.requestHash(),a.requesterId(),a.targetType(),a.targetId(),
            a.status().name(),a.approverId(),a.decisionReason(),a.requestedAt(),a.expiresAt(),a.decidedAt(),a.consumedAt(),a.version());}
}
