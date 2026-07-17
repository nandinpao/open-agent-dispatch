package com.opensocket.aievent.database.persistence.execution.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.database.persistence.execution.po.TaskCallbackPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "task.callback", name = "store", havingValue = "MYBATIS")
public class TaskCallbackPersistenceConverter {
    private final ObjectMapper objectMapper;

    public TaskCallbackPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskCallbackPo toPo(TaskCallbackRecord record) {
            TaskCallbackPo po = new TaskCallbackPo();
            po.setCallbackId(record.getCallbackId());
            po.setCallbackType(record.getCallbackType() == null ? null : record.getCallbackType().name());
            po.setTaskId(record.getTaskId());
            po.setDispatchRequestId(record.getDispatchRequestId());
            po.setAssignmentId(record.getAssignmentId());
            po.setAgentId(record.getAgentId());
            po.setOwnerGatewayNodeId(record.getOwnerGatewayNodeId());
            po.setAgentSessionId(record.getAgentSessionId());
            po.setAttemptNo(record.getAttemptNo());
            po.setFencingToken(record.getFencingToken());
            po.setAccepted(record.isAccepted());
            po.setIgnoredReason(record.getIgnoredReason());
            po.setMessage(record.getMessage());
            po.setProgressPercent(record.getProgressPercent());
            po.setErrorCode(record.getErrorCode());
            po.setErrorMessage(record.getErrorMessage());
            po.setPayloadJson(toJson(record.getPayload()));
            po.setOccurredAt(record.getOccurredAt());
            po.setProcessedAt(record.getProcessedAt());
            po.setDuplicate(record.isDuplicate());
            po.setIdempotencyKey(record.getIdempotencyKey());
            po.setCallbackFingerprint(record.getCallbackFingerprint());
            po.setReplayDetected(record.isReplayDetected());
            po.setPreviousTaskStatus(record.getPreviousTaskStatus());
            po.setNewTaskStatus(record.getNewTaskStatus());
            po.setPreviousDispatchStatus(record.getPreviousDispatchStatus());
            po.setNewDispatchStatus(record.getNewDispatchStatus());
            return po;
        }

    public TaskCallbackRecord toRecord(TaskCallbackPo po) {
            TaskCallbackRecord record = new TaskCallbackRecord();
            record.setCallbackId(po.getCallbackId());
            record.setCallbackType(po.getCallbackType() == null ? null : TaskCallbackType.valueOf(po.getCallbackType()));
            record.setTaskId(po.getTaskId());
            record.setDispatchRequestId(po.getDispatchRequestId());
            record.setAssignmentId(po.getAssignmentId());
            record.setAgentId(po.getAgentId());
            record.setOwnerGatewayNodeId(po.getOwnerGatewayNodeId());
            record.setAgentSessionId(po.getAgentSessionId());
            record.setAttemptNo(po.getAttemptNo());
            record.setFencingToken(po.getFencingToken());
            record.setAccepted(po.isAccepted());
            record.setIgnoredReason(po.getIgnoredReason());
            record.setMessage(po.getMessage());
            record.setProgressPercent(po.getProgressPercent());
            record.setErrorCode(po.getErrorCode());
            record.setErrorMessage(po.getErrorMessage());
            record.setPayload(fromJson(po.getPayloadJson()));
            record.setOccurredAt(po.getOccurredAt());
            record.setProcessedAt(po.getProcessedAt());
            record.setDuplicate(po.isDuplicate());
            record.setIdempotencyKey(po.getIdempotencyKey());
            record.setCallbackFingerprint(po.getCallbackFingerprint());
            record.setReplayDetected(po.isReplayDetected());
            record.setPreviousTaskStatus(po.getPreviousTaskStatus());
            record.setNewTaskStatus(po.getNewTaskStatus());
            record.setPreviousDispatchStatus(po.getPreviousDispatchStatus());
            record.setNewDispatchStatus(po.getNewDispatchStatus());
            return record;
        }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); } catch (Exception ex) { return "{}"; }
        }

    public Map<String, Object> fromJson(String json) {
            try { if (json == null || json.isBlank()) return new LinkedHashMap<>(); return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); } catch (Exception ex) { return new LinkedHashMap<>(); }
        }
}
