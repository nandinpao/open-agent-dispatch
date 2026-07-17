package com.opensocket.aievent.database.persistence.adapter.converter;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.database.persistence.adapter.po.AdapterActionPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "adapter-actions", name = "store", havingValue = "MYBATIS")
public class AdapterActionPersistenceConverter {
    private final ObjectMapper objectMapper;

    public AdapterActionPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AdapterActionPo toPo(AdapterAction action) {
            AdapterActionPo po = new AdapterActionPo();
            po.setActionId(action.getActionId());
            po.setIdempotencyKey(action.getIdempotencyKey());
            po.setIncidentId(action.getIncidentId());
            po.setTaskId(action.getTaskId());
            po.setDispatchRequestId(action.getDispatchRequestId());
            po.setAssignmentId(action.getAssignmentId());
            po.setAgentId(action.getAgentId());
            po.setAdapterName(action.getAdapterName());
            po.setAdapterType(action.getAdapterType() == null ? null : action.getAdapterType().name());
            po.setActionType(action.getActionType() == null ? null : action.getActionType().name());
            po.setStatus(action.getStatus() == null ? null : action.getStatus().name());
            po.setReason(action.getReason());
            po.setRequestHash(action.getRequestHash());
            po.setResponseRef(action.getResponseRef());
            po.setPayloadJson(toJson(action.getPayload()));
            po.setCreatedAt(action.getCreatedAt());
            po.setUpdatedAt(action.getUpdatedAt());
            po.setExecutingAt(action.getExecutingAt());
            po.setCompletedAt(action.getCompletedAt());
            po.setFailedAt(action.getFailedAt());
            po.setNextAttemptAt(action.getNextAttemptAt());
            po.setRetryWaitingAt(action.getRetryWaitingAt());
            po.setExecutorUnavailableAt(action.getExecutorUnavailableAt());
            po.setClaimedBy(action.getClaimedBy());
            po.setClaimedAt(action.getClaimedAt());
            po.setLeaseExpiresAt(action.getLeaseExpiresAt());
            po.setWorkerHeartbeatAt(action.getWorkerHeartbeatAt());
            po.setAttemptCount(action.getAttemptCount());
            po.setMaxAttempts(action.getMaxAttempts());
            po.setExecutorName(action.getExecutorName());
            po.setLastError(action.getLastError());
            return po;
        }

    public AdapterAction toAction(AdapterActionPo po) {
            AdapterAction action = new AdapterAction();
            action.setActionId(po.getActionId());
            action.setIdempotencyKey(po.getIdempotencyKey());
            action.setIncidentId(po.getIncidentId());
            action.setTaskId(po.getTaskId());
            action.setDispatchRequestId(po.getDispatchRequestId());
            action.setAssignmentId(po.getAssignmentId());
            action.setAgentId(po.getAgentId());
            action.setAdapterName(po.getAdapterName());
            action.setAdapterType(po.getAdapterType() == null ? null : AdapterType.valueOf(po.getAdapterType()));
            action.setActionType(po.getActionType() == null ? null : AdapterActionType.valueOf(po.getActionType()));
            action.setStatus(po.getStatus() == null ? null : AdapterActionStatus.valueOf(po.getStatus()));
            action.setReason(po.getReason());
            action.setRequestHash(po.getRequestHash());
            action.setResponseRef(po.getResponseRef());
            action.setPayload(fromJson(po.getPayloadJson()));
            action.setCreatedAt(po.getCreatedAt());
            action.setUpdatedAt(po.getUpdatedAt());
            action.setExecutingAt(po.getExecutingAt());
            action.setCompletedAt(po.getCompletedAt());
            action.setFailedAt(po.getFailedAt());
            action.setNextAttemptAt(po.getNextAttemptAt());
            action.setRetryWaitingAt(po.getRetryWaitingAt());
            action.setExecutorUnavailableAt(po.getExecutorUnavailableAt());
            action.setClaimedBy(po.getClaimedBy());
            action.setClaimedAt(po.getClaimedAt());
            action.setLeaseExpiresAt(po.getLeaseExpiresAt());
            action.setWorkerHeartbeatAt(po.getWorkerHeartbeatAt());
            action.setAttemptCount(po.getAttemptCount());
            action.setMaxAttempts(po.getMaxAttempts());
            action.setExecutorName(po.getExecutorName());
            action.setLastError(po.getLastError());
            return action;
        }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); } catch (Exception ex) { return "{}"; }
        }

    public Map<String, Object> fromJson(String json) {
            try { if (json == null || json.isBlank()) return new LinkedHashMap<>(); return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); } catch (Exception ex) { return new LinkedHashMap<>(); }
        }
}
