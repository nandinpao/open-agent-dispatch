package com.opensocket.aievent.database.persistence.task.converter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.task.po.TaskPo;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class TaskPersistenceConverter {
    private final ObjectMapper objectMapper;

    public TaskPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskPo toPo(TaskRecord task) {
            TaskPo po = new TaskPo();
            po.setTaskId(task.getTaskId());
            po.setIncidentId(task.getIncidentId());
            po.setSourceEventId(task.getSourceEventId());
            po.setSourceSystem(task.getSourceSystem());
            po.setEventStage(task.getEventStage());
            po.setOriginSourceSystem(task.getOriginSourceSystem());
            po.setTargetSystem(task.getTargetSystem());
            po.setTaskType(task.getTaskType() == null
                    ? TaskType.INCIDENT_RESPONSE.name()
                    : task.getTaskType().name());
            po.setTaskTypeCode(trimToNull(task.getTaskTypeCode()));
            po.setStatus(task.getStatus() == null ? null : task.getStatus().name());
            po.setPriority(task.getPriority() == null ? null : task.getPriority().name());
            po.setTenantId(task.getTenantId());
            po.setSiteId(task.getSiteId());
            po.setPlantId(task.getPlantId());
            po.setObjectType(task.getObjectType());
            po.setObjectId(task.getObjectId());
            po.setEventType(task.getEventType());
            po.setErrorCode(task.getErrorCode());
            po.setRequestedSkill(task.getRequestedSkill());
            po.setHandoffMode(task.getHandoffMode());
            po.setCorrelationId(task.getCorrelationId());
            po.setParentTaskId(task.getParentTaskId());
            po.setMatchedFlowId(task.getMatchedFlowId());
            po.setMatchedRuleId(task.getMatchedRuleId());
            po.setAssignedPoolId(task.getAssignedPoolId());
            po.setTargetPoolId(task.getTargetPoolId());
            po.setClassificationStatus(firstNonBlank(task.getClassificationStatus(), "CLASSIFIED"));
            po.setClassificationResultJson(firstNonBlank(task.getClassificationResultJson(), "{}"));
            po.setRoutingPath(task.getRoutingPath());
            po.setRoutingPolicy(task.getRoutingPolicy());
            po.setRequiredCapabilitiesJson(toJson(task.getRequiredCapabilities()));
            po.setCreatedReason(task.getCreatedReason());
            po.setOccurrenceCountAtCreation(task.getOccurrenceCountAtCreation());
            po.setCreatedAt(task.getCreatedAt());
            po.setUpdatedAt(task.getUpdatedAt());
            po.setTimeoutAt(task.getTimeoutAt());
            po.setTerminalAt(task.getTerminalAt());
            po.setReassignmentCount(task.getReassignmentCount());
            po.setNextDispatchAttemptAt(task.getNextDispatchAttemptAt());
            po.setDispatchAttemptCount(task.getDispatchAttemptCount());
            po.setDispatchRetryReason(task.getDispatchRetryReason());
            po.setDispatchRecoveryClaimedBy(task.getDispatchRecoveryClaimedBy());
            po.setDispatchRecoveryClaimUntil(task.getDispatchRecoveryClaimUntil());
            po.setLifecycleReason(task.getLifecycleReason());
            po.setExternalExecutionKey(task.getExternalExecutionKey());
            return po;
        }

    public TaskRecord toTask(TaskPo po) {
            TaskRecord task = new TaskRecord();
            task.setTaskId(po.getTaskId());
            task.setIncidentId(po.getIncidentId());
            task.setSourceEventId(po.getSourceEventId());
            task.setSourceSystem(po.getSourceSystem());
            task.setEventStage(po.getEventStage());
            task.setOriginSourceSystem(po.getOriginSourceSystem());
            task.setTargetSystem(po.getTargetSystem());
            String storedTaskType = trimToNull(po.getTaskType());
            TaskType platformTaskType = parsePlatformTaskType(storedTaskType);
            task.setTaskType(platformTaskType);
            task.setTaskTypeCode(firstNonBlank(
                    trimToNull(po.getTaskTypeCode()),
                    isPlatformTaskType(storedTaskType) ? null : storedTaskType));
            task.setStatus(TaskStatus.fromStorageValue(po.getStatus()));
            task.setPriority(po.getPriority() == null ? null : TaskPriority.valueOf(po.getPriority()));
            task.setTenantId(po.getTenantId());
            task.setSiteId(po.getSiteId());
            task.setPlantId(po.getPlantId());
            task.setObjectType(po.getObjectType());
            task.setObjectId(po.getObjectId());
            task.setEventType(po.getEventType());
            task.setErrorCode(po.getErrorCode());
            task.setRequestedSkill(po.getRequestedSkill());
            task.setHandoffMode(po.getHandoffMode());
            task.setCorrelationId(po.getCorrelationId());
            task.setParentTaskId(po.getParentTaskId());
            task.setMatchedFlowId(po.getMatchedFlowId());
            task.setMatchedRuleId(po.getMatchedRuleId());
            task.setAssignedPoolId(po.getAssignedPoolId());
            task.setTargetPoolId(po.getTargetPoolId());
            task.setClassificationStatus(firstNonBlank(po.getClassificationStatus(), "CLASSIFIED"));
            task.setClassificationResultJson(firstNonBlank(po.getClassificationResultJson(), "{}"));
            task.setRoutingPath(po.getRoutingPath());
            task.setRoutingPolicy(po.getRoutingPolicy());
            task.setRequiredCapabilities(fromJson(po.getRequiredCapabilitiesJson()));
            task.setCreatedReason(po.getCreatedReason());
            task.setOccurrenceCountAtCreation(po.getOccurrenceCountAtCreation());
            task.setCreatedAt(po.getCreatedAt());
            task.setUpdatedAt(po.getUpdatedAt());
            task.setTimeoutAt(po.getTimeoutAt());
            task.setTerminalAt(po.getTerminalAt());
            task.setReassignmentCount(po.getReassignmentCount());
            task.setNextDispatchAttemptAt(po.getNextDispatchAttemptAt());
            task.setDispatchAttemptCount(po.getDispatchAttemptCount());
            task.setDispatchRetryReason(po.getDispatchRetryReason());
            task.setDispatchRecoveryClaimedBy(po.getDispatchRecoveryClaimedBy());
            task.setDispatchRecoveryClaimUntil(po.getDispatchRecoveryClaimUntil());
            task.setLifecycleReason(po.getLifecycleReason());
            task.setExternalExecutionKey(po.getExternalExecutionKey());
            return task;
        }

    private TaskType parsePlatformTaskType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TaskType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Historical rows may contain a tenant/business task code in tasks.task_type.
            // Preserve that opaque value in taskTypeCode and use the platform lifecycle
            // category only for backward-compatible runtime processing.
            return TaskType.INCIDENT_RESPONSE;
        }
    }

    private boolean isPlatformTaskType(String value) {
        if (value == null) {
            return false;
        }
        try {
            TaskType.valueOf(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String toJson(Object value) {
            try { return objectMapper.writeValueAsString(value == null ? List.of() : value); } catch (Exception ex) { return "[]"; }
        }

    public List<String> fromJson(String json) {
            try { if (json == null || json.isBlank()) return List.of(); return objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception ex) { return List.of(); }
        }
}
