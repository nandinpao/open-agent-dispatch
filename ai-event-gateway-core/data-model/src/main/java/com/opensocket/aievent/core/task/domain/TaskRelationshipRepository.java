package com.opensocket.aievent.core.task.domain;

import java.util.List;
import java.util.Optional;

public interface TaskRelationshipRepository {
    TaskRelationship save(TaskRelationship relationship);
    Optional<TaskRelationship> findById(String tenantId, String relationshipId);
    Optional<TaskRelationship> findByIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<TaskRelationship> findNatural(String tenantId, String fromTaskId, String toTaskId, TaskRelationshipType relationshipType);
    List<TaskRelationship> findOutbound(String tenantId, String taskId, int limit);
    List<TaskRelationship> findInbound(String tenantId, String taskId, int limit);
    boolean delete(String tenantId, String relationshipId);
    String mode();
}
