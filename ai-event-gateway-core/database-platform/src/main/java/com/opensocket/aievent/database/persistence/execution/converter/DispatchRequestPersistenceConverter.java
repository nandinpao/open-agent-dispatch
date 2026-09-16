package com.opensocket.aievent.database.persistence.execution.converter;




import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.dispatch.DispatchEligibilityStatus;
import com.opensocket.aievent.core.dispatch.DispatchMethod;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchOutboxStatus;
import com.opensocket.aievent.core.dispatch.DispatchRecoveryClassification;
import com.opensocket.aievent.core.dispatch.DispatchReviewMode;
import com.opensocket.aievent.core.dispatch.NettyDispatchCommand;
import com.opensocket.aievent.database.persistence.execution.po.DispatchRequestPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "dispatch", name = "request-store", havingValue = "MYBATIS")
public class DispatchRequestPersistenceConverter {
    private final ObjectMapper objectMapper;

    public DispatchRequestPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DispatchRequestPo toPo(DispatchRequest request) {
            DispatchRequestPo po = new DispatchRequestPo();
            po.setDispatchRequestId(request.getDispatchRequestId());
            po.setTenantId(request.getTenantId());
            po.setAssignmentId(request.getAssignmentId());
            po.setExecutionAuthorityVersion(request.getExecutionAuthorityVersion());
            po.setCanonicalExecutionAssignmentId(request.getCanonicalExecutionAssignmentId());
            po.setAuthorityProvenance(request.getAuthorityProvenance() == null ? null : request.getAuthorityProvenance().name());
            po.setTaskId(request.getTaskId());
            po.setIncidentId(request.getIncidentId());
            po.setAgentId(request.getAgentId());
            po.setOwnerGatewayNodeId(request.getOwnerGatewayNodeId());
            po.setAgentSessionId(request.getAgentSessionId());
            po.setSiteId(request.getSiteId());
            po.setStatus(request.getStatus() == null ? null : request.getStatus().name());
            po.setReviewMode(request.getReviewMode() == null ? null : request.getReviewMode().name());
            po.setEligibilityStatus(request.getEligibilityStatus() == null ? null : request.getEligibilityStatus().name());
            po.setDispatchMethod(request.getDispatchMethod() == null ? null : request.getDispatchMethod().name());
            po.setGatewayDispatchPath(request.getGatewayDispatchPath());
            po.setDispatchToken(request.getDispatchToken());
            po.setReason(request.getReason());
            po.setCommandJson(toJson(request.getCommand()));
            po.setCreatedAt(request.getCreatedAt());
            po.setUpdatedAt(request.getUpdatedAt());
            po.setApprovedAt(request.getApprovedAt());
            po.setDispatchedAt(request.getDispatchedAt());
            po.setFailedAt(request.getFailedAt());
            po.setAttemptCount(request.getAttemptCount());
            po.setLastError(request.getLastError());
            po.setLastCallbackId(request.getLastCallbackId());
            po.setCompletedAt(request.getCompletedAt());
            po.setTimedOutAt(request.getTimedOutAt());
            po.setRetryWaitingAt(request.getRetryWaitingAt());
            po.setNextRetryAt(request.getNextRetryAt());
            po.setDeadLetterAt(request.getDeadLetterAt());
            po.setClaimedBy(request.getClaimedBy());
            po.setClaimStartedAt(request.getClaimStartedAt());
            po.setClaimUntil(request.getClaimUntil());
            po.setOutboxStatus(request.getOutboxStatus() == null ? null : request.getOutboxStatus().name());
            po.setClaimToken(request.getClaimToken());
            po.setClaimHeartbeatAt(request.getClaimHeartbeatAt());
            po.setDispatchTokenHash(request.getDispatchTokenHash());
            po.setFencingTokenHash(request.getFencingTokenHash());
            po.setRuntimeSessionId(request.getRuntimeSessionId());
            po.setAckEvidenceId(request.getAckEvidenceId());
            po.setAckedAt(request.getAckedAt());
            po.setRecoveryClassification(request.getRecoveryClassification() == null ? null : request.getRecoveryClassification().name());
            po.setUncertainSince(request.getUncertainSince());
            po.setLastReconciledAt(request.getLastReconciledAt());
            po.setReconciliationCount(request.getReconciliationCount());
            po.setRowVersion(request.getRowVersion());
            return po;
        }

    public DispatchRequest toRequest(DispatchRequestPo po) {
            DispatchRequest request = new DispatchRequest();
            request.setDispatchRequestId(po.getDispatchRequestId());
            request.setTenantId(po.getTenantId());
            request.setAssignmentId(po.getAssignmentId());
            request.setExecutionAuthorityVersion(po.getExecutionAuthorityVersion());
            request.setCanonicalExecutionAssignmentId(po.getCanonicalExecutionAssignmentId());
            request.setAuthorityProvenance(po.getAuthorityProvenance() == null ? com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance.LEGACY_COMPATIBILITY : com.opensocket.aievent.core.dispatch.DispatchAuthorityProvenance.valueOf(po.getAuthorityProvenance()));
            request.setTaskId(po.getTaskId());
            request.setIncidentId(po.getIncidentId());
            request.setAgentId(po.getAgentId());
            request.setOwnerGatewayNodeId(po.getOwnerGatewayNodeId());
            request.setAgentSessionId(po.getAgentSessionId());
            request.setSiteId(po.getSiteId());
            request.setStatus(po.getStatus() == null ? null : DispatchRequestStatus.valueOf(po.getStatus()));
            request.setReviewMode(po.getReviewMode() == null ? null : DispatchReviewMode.valueOf(po.getReviewMode()));
            request.setEligibilityStatus(po.getEligibilityStatus() == null ? null : DispatchEligibilityStatus.valueOf(po.getEligibilityStatus()));
            request.setDispatchMethod(po.getDispatchMethod() == null ? null : DispatchMethod.valueOf(po.getDispatchMethod()));
            request.setGatewayDispatchPath(po.getGatewayDispatchPath());
            request.setDispatchToken(po.getDispatchToken());
            request.setReason(po.getReason());
            request.setCommand(fromJson(po.getCommandJson()));
            request.setCreatedAt(po.getCreatedAt());
            request.setUpdatedAt(po.getUpdatedAt());
            request.setApprovedAt(po.getApprovedAt());
            request.setDispatchedAt(po.getDispatchedAt());
            request.setFailedAt(po.getFailedAt());
            request.setAttemptCount(po.getAttemptCount());
            request.setLastError(po.getLastError());
            request.setLastCallbackId(po.getLastCallbackId());
            request.setCompletedAt(po.getCompletedAt());
            request.setTimedOutAt(po.getTimedOutAt());
            request.setRetryWaitingAt(po.getRetryWaitingAt());
            request.setNextRetryAt(po.getNextRetryAt());
            request.setDeadLetterAt(po.getDeadLetterAt());
            request.setClaimedBy(po.getClaimedBy());
            request.setClaimStartedAt(po.getClaimStartedAt());
            request.setClaimUntil(po.getClaimUntil());
            request.setOutboxStatus(po.getOutboxStatus() == null ? DispatchOutboxStatus.PENDING : DispatchOutboxStatus.valueOf(po.getOutboxStatus()));
            request.setClaimToken(po.getClaimToken());
            request.setClaimHeartbeatAt(po.getClaimHeartbeatAt());
            request.setDispatchTokenHash(po.getDispatchTokenHash());
            request.setFencingTokenHash(po.getFencingTokenHash());
            request.setRuntimeSessionId(po.getRuntimeSessionId());
            request.setAckEvidenceId(po.getAckEvidenceId());
            request.setAckedAt(po.getAckedAt());
            request.setRecoveryClassification(po.getRecoveryClassification() == null ? DispatchRecoveryClassification.NONE : DispatchRecoveryClassification.valueOf(po.getRecoveryClassification()));
            request.setUncertainSince(po.getUncertainSince());
            request.setLastReconciledAt(po.getLastReconciledAt());
            request.setReconciliationCount(po.getReconciliationCount());
            request.setRowVersion(po.getRowVersion());
            return request;
        }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value); } catch (Exception ex) { return "{}"; }
        }

    public NettyDispatchCommand fromJson(String json) {
            try { if (json == null || json.isBlank()) return null; return objectMapper.readValue(json, NettyDispatchCommand.class); } catch (Exception ex) { return null; }
        }
}
