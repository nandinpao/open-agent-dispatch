package com.opensocket.aievent.database.persistence.execution.converter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.dispatch.DispatchEligibilityStatus;
import com.opensocket.aievent.core.dispatch.DispatchMethod;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
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
            po.setAssignmentId(request.getAssignmentId());
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
            return po;
        }

    public DispatchRequest toRequest(DispatchRequestPo po) {
            DispatchRequest request = new DispatchRequest();
            request.setDispatchRequestId(po.getDispatchRequestId());
            request.setAssignmentId(po.getAssignmentId());
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
            return request;
        }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value); } catch (Exception ex) { return "{}"; }
        }

    public NettyDispatchCommand fromJson(String json) {
            try { if (json == null || json.isBlank()) return null; return objectMapper.readValue(json, NettyDispatchCommand.class); } catch (Exception ex) { return null; }
        }
}
