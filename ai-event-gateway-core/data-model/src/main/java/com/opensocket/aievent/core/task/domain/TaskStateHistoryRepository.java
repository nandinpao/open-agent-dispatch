package com.opensocket.aievent.core.task.domain;

import java.util.List;
import java.util.Optional;

public interface TaskStateHistoryRepository {
    default TaskStateHistoryEntry save(TaskStateHistoryEntry entry) { return entry; }
    List<TaskStateHistoryEntry> findByTask(String tenantId, String taskId, int limit);
    Optional<TaskStateHistoryEntry> findByIdempotencyKey(String tenantId, String idempotencyKey);
    String mode();
}
