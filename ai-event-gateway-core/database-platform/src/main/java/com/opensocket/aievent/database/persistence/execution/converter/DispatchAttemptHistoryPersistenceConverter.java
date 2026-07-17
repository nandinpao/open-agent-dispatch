package com.opensocket.aievent.database.persistence.execution.converter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.database.persistence.execution.po.DispatchAttemptHistoryPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "dispatch", name = "attempt-history-store", havingValue = "MYBATIS")
public class DispatchAttemptHistoryPersistenceConverter {
    public DispatchAttemptHistoryPo toPo(DispatchAttemptHistoryRecord record) {
        DispatchAttemptHistoryPo po = new DispatchAttemptHistoryPo();
        po.setHistoryId(record.getHistoryId());
        po.setTaskId(record.getTaskId());
        po.setIncidentId(record.getIncidentId());
        po.setAssignmentId(record.getAssignmentId());
        po.setDispatchRequestId(record.getDispatchRequestId());
        po.setAgentId(record.getAgentId());
        po.setOwnerGatewayNodeId(record.getOwnerGatewayNodeId());
        po.setAgentSessionId(record.getAgentSessionId());
        po.setSiteId(record.getSiteId());
        po.setRoutingDecisionId(record.getRoutingDecisionId());
        po.setEventType(record.getEventType());
        po.setStatus(record.getStatus());
        po.setAttemptNo(record.getAttemptNo());
        po.setTaskDispatchAttemptNo(record.getTaskDispatchAttemptNo());
        po.setReason(record.getReason());
        po.setErrorCode(record.getErrorCode());
        po.setErrorMessage(record.getErrorMessage());
        po.setNextAttemptAt(record.getNextAttemptAt());
        po.setRuntimeBackoffUntil(record.getRuntimeBackoffUntil());
        po.setWorkerId(record.getWorkerId());
        po.setClaimUntil(record.getClaimUntil());
        po.setPayloadJson(record.getPayloadJson());
        po.setOccurredAt(record.getOccurredAt());
        po.setCreatedAt(record.getCreatedAt());
        return po;
    }

    public DispatchAttemptHistoryRecord toRecord(DispatchAttemptHistoryPo po) {
        DispatchAttemptHistoryRecord record = new DispatchAttemptHistoryRecord();
        record.setHistoryId(po.getHistoryId());
        record.setTaskId(po.getTaskId());
        record.setIncidentId(po.getIncidentId());
        record.setAssignmentId(po.getAssignmentId());
        record.setDispatchRequestId(po.getDispatchRequestId());
        record.setAgentId(po.getAgentId());
        record.setOwnerGatewayNodeId(po.getOwnerGatewayNodeId());
        record.setAgentSessionId(po.getAgentSessionId());
        record.setSiteId(po.getSiteId());
        record.setRoutingDecisionId(po.getRoutingDecisionId());
        record.setEventType(po.getEventType());
        record.setStatus(po.getStatus());
        record.setAttemptNo(po.getAttemptNo());
        record.setTaskDispatchAttemptNo(po.getTaskDispatchAttemptNo());
        record.setReason(po.getReason());
        record.setErrorCode(po.getErrorCode());
        record.setErrorMessage(po.getErrorMessage());
        record.setNextAttemptAt(po.getNextAttemptAt());
        record.setRuntimeBackoffUntil(po.getRuntimeBackoffUntil());
        record.setWorkerId(po.getWorkerId());
        record.setClaimUntil(po.getClaimUntil());
        record.setPayloadJson(po.getPayloadJson());
        record.setOccurredAt(po.getOccurredAt());
        record.setCreatedAt(po.getCreatedAt());
        return record;
    }
}
