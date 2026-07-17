package com.opensocket.aievent.database.persistence.execution.converter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.dispatch.RecoveryApprovalRequest;
import com.opensocket.aievent.core.dispatch.RecoveryApprovalStatus;
import com.opensocket.aievent.database.persistence.execution.po.RecoveryApprovalRequestPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "core.recovery.approval", name = "store", havingValue = "MYBATIS")
public class RecoveryApprovalRequestPersistenceConverter {
    public RecoveryApprovalRequestPo toPo(RecoveryApprovalRequest request) {
        RecoveryApprovalRequestPo po = new RecoveryApprovalRequestPo();
        po.setApprovalId(request.getApprovalId());
        po.setStatus(request.getStatus() == null ? null : request.getStatus().name());
        po.setAction(request.getAction());
        po.setTargetType(request.getTargetType());
        po.setTargetId(request.getTargetId());
        po.setDispatchRequestId(request.getDispatchRequestId());
        po.setTaskId(request.getTaskId());
        po.setAgentId(request.getAgentId());
        po.setRiskLevel(request.getRiskLevel());
        po.setRequestedBy(request.getRequestedBy());
        po.setRequesterPrincipal(request.getRequesterPrincipal());
        po.setRequesterRole(request.getRequesterRole());
        po.setRequestReason(request.getRequestReason());
        po.setRequestId(request.getRequestId());
        po.setRequestClientAddress(request.getRequestClientAddress());
        po.setRequestUserAgent(request.getRequestUserAgent());
        po.setApprovalReason(request.getApprovalReason());
        po.setApprovedBy(request.getApprovedBy());
        po.setApproverPrincipal(request.getApproverPrincipal());
        po.setApproverRole(request.getApproverRole());
        po.setApprovalRequestId(request.getApprovalRequestId());
        po.setApprovalClientAddress(request.getApprovalClientAddress());
        po.setApprovalUserAgent(request.getApprovalUserAgent());
        po.setRejectedBy(request.getRejectedBy());
        po.setRejectedReason(request.getRejectedReason());
        po.setCancelledBy(request.getCancelledBy());
        po.setCancelledReason(request.getCancelledReason());
        po.setExecutionResult(request.getExecutionResult());
        po.setExecutionError(request.getExecutionError());
        po.setExpiresAt(request.getExpiresAt());
        po.setApprovedAt(request.getApprovedAt());
        po.setExecutedAt(request.getExecutedAt());
        po.setRejectedAt(request.getRejectedAt());
        po.setCancelledAt(request.getCancelledAt());
        po.setCreatedAt(request.getCreatedAt());
        po.setUpdatedAt(request.getUpdatedAt());
        po.setPayloadJson(request.getPayloadJson());
        return po;
    }

    public RecoveryApprovalRequest toRecord(RecoveryApprovalRequestPo po) {
        RecoveryApprovalRequest request = new RecoveryApprovalRequest();
        request.setApprovalId(po.getApprovalId());
        request.setStatus(po.getStatus() == null ? null : RecoveryApprovalStatus.valueOf(po.getStatus()));
        request.setAction(po.getAction());
        request.setTargetType(po.getTargetType());
        request.setTargetId(po.getTargetId());
        request.setDispatchRequestId(po.getDispatchRequestId());
        request.setTaskId(po.getTaskId());
        request.setAgentId(po.getAgentId());
        request.setRiskLevel(po.getRiskLevel());
        request.setRequestedBy(po.getRequestedBy());
        request.setRequesterPrincipal(po.getRequesterPrincipal());
        request.setRequesterRole(po.getRequesterRole());
        request.setRequestReason(po.getRequestReason());
        request.setRequestId(po.getRequestId());
        request.setRequestClientAddress(po.getRequestClientAddress());
        request.setRequestUserAgent(po.getRequestUserAgent());
        request.setApprovalReason(po.getApprovalReason());
        request.setApprovedBy(po.getApprovedBy());
        request.setApproverPrincipal(po.getApproverPrincipal());
        request.setApproverRole(po.getApproverRole());
        request.setApprovalRequestId(po.getApprovalRequestId());
        request.setApprovalClientAddress(po.getApprovalClientAddress());
        request.setApprovalUserAgent(po.getApprovalUserAgent());
        request.setRejectedBy(po.getRejectedBy());
        request.setRejectedReason(po.getRejectedReason());
        request.setCancelledBy(po.getCancelledBy());
        request.setCancelledReason(po.getCancelledReason());
        request.setExecutionResult(po.getExecutionResult());
        request.setExecutionError(po.getExecutionError());
        request.setExpiresAt(po.getExpiresAt());
        request.setApprovedAt(po.getApprovedAt());
        request.setExecutedAt(po.getExecutedAt());
        request.setRejectedAt(po.getRejectedAt());
        request.setCancelledAt(po.getCancelledAt());
        request.setCreatedAt(po.getCreatedAt());
        request.setUpdatedAt(po.getUpdatedAt());
        request.setPayloadJson(po.getPayloadJson());
        return request;
    }
}
