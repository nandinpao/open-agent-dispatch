package com.opensocket.aievent.database.persistence.task.converter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.task.po.TaskParticipantPo;
import com.opensocket.aievent.database.persistence.task.po.TaskRelationshipPo;
import com.opensocket.aievent.database.persistence.task.po.TaskStateHistoryPo;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.domain.*;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class TaskDomainPersistenceConverter {
    public TaskRelationshipPo toPo(TaskRelationship value) {
        TaskRelationshipPo po = new TaskRelationshipPo();
        po.setTenantId(value.getTenantId()); po.setRelationshipId(value.getRelationshipId());
        po.setFromTaskId(value.getFromTaskId()); po.setToTaskId(value.getToTaskId());
        po.setRelationshipType(value.getRelationshipType().name()); po.setDirection(value.getDirection().name());
        po.setReasonCode(value.getReasonCode()); po.setDescription(value.getDescription());
        po.setReferenceVisibility(value.getReferenceVisibility().name()); po.setIdempotencyKey(value.getIdempotencyKey());
        po.setCreatedByType(value.getCreatedByType().name()); po.setCreatedById(value.getCreatedById());
        po.setCreatedAt(value.getCreatedAt()); po.setVersion(value.getVersion());
        return po;
    }
    public TaskRelationship toDomain(TaskRelationshipPo po) {
        TaskRelationship value = new TaskRelationship();
        value.setTenantId(po.getTenantId()); value.setRelationshipId(po.getRelationshipId());
        value.setFromTaskId(po.getFromTaskId()); value.setToTaskId(po.getToTaskId());
        value.setRelationshipType(TaskRelationshipType.valueOf(po.getRelationshipType()));
        value.setDirection(TaskRelationshipDirection.valueOf(po.getDirection()));
        value.setReasonCode(po.getReasonCode()); value.setDescription(po.getDescription());
        value.setReferenceVisibility(TaskReferenceVisibility.valueOf(po.getReferenceVisibility()));
        value.setIdempotencyKey(po.getIdempotencyKey()); value.setCreatedByType(TaskActorType.valueOf(po.getCreatedByType()));
        value.setCreatedById(po.getCreatedById()); value.setCreatedAt(po.getCreatedAt()); value.setVersion(po.getVersion());
        return value;
    }
    public TaskParticipantPo toPo(TaskParticipant value) {
        TaskParticipantPo po = new TaskParticipantPo();
        po.setTenantId(value.getTenantId()); po.setParticipantId(value.getParticipantId()); po.setTaskId(value.getTaskId());
        po.setParticipantType(value.getParticipantType().name()); po.setParticipantRefId(value.getParticipantRefId());
        po.setParticipantRole(value.getParticipantRole().name()); po.setVisibilityLevel(value.getVisibilityLevel().name());
        po.setOperationLevel(value.getOperationLevel().name()); po.setCreatedAt(value.getCreatedAt());
        po.setCreatedBy(value.getCreatedBy()); po.setVersion(value.getVersion());
        return po;
    }
    public TaskParticipant toDomain(TaskParticipantPo po) {
        TaskParticipant value = new TaskParticipant();
        value.setTenantId(po.getTenantId()); value.setParticipantId(po.getParticipantId()); value.setTaskId(po.getTaskId());
        value.setParticipantType(TaskParticipantType.valueOf(po.getParticipantType())); value.setParticipantRefId(po.getParticipantRefId());
        value.setParticipantRole(TaskParticipantRole.valueOf(po.getParticipantRole()));
        value.setVisibilityLevel(TaskParticipantVisibilityLevel.valueOf(po.getVisibilityLevel()));
        value.setOperationLevel(TaskParticipantOperationLevel.valueOf(po.getOperationLevel()));
        value.setCreatedAt(po.getCreatedAt()); value.setCreatedBy(po.getCreatedBy()); value.setVersion(po.getVersion());
        return value;
    }
    public TaskStateHistoryPo toPo(TaskStateHistoryEntry value) {
        TaskStateHistoryPo po = new TaskStateHistoryPo();
        po.setTenantId(value.getTenantId()); po.setHistoryId(value.getHistoryId()); po.setTaskId(value.getTaskId());
        po.setFromStatus(value.getFromStatus() == null ? null : value.getFromStatus().name());
        po.setToStatus(value.getToStatus().name()); po.setReasonCode(value.getReasonCode()); po.setReason(value.getReason());
        po.setActorType(value.getActorType().name()); po.setActorId(value.getActorId()); po.setCorrelationId(value.getCorrelationId());
        po.setTransitionAt(value.getTransitionAt()); po.setTaskVersion(value.getTaskVersion()); po.setIdempotencyKey(value.getIdempotencyKey());
        return po;
    }
    public TaskStateHistoryEntry toDomain(TaskStateHistoryPo po) {
        TaskStateHistoryEntry value = new TaskStateHistoryEntry();
        value.setTenantId(po.getTenantId()); value.setHistoryId(po.getHistoryId()); value.setTaskId(po.getTaskId());
        value.setFromStatus(po.getFromStatus() == null ? null : TaskStatus.fromStorageValue(po.getFromStatus()));
        value.setToStatus(TaskStatus.fromStorageValue(po.getToStatus())); value.setReasonCode(po.getReasonCode());
        value.setReason(po.getReason()); value.setActorType(TaskActorType.valueOf(po.getActorType()));
        value.setActorId(po.getActorId()); value.setCorrelationId(po.getCorrelationId());
        value.setTransitionAt(po.getTransitionAt()); value.setTaskVersion(po.getTaskVersion()); value.setIdempotencyKey(po.getIdempotencyKey());
        return value;
    }
}
