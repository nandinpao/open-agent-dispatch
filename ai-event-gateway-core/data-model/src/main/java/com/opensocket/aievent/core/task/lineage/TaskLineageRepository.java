package com.opensocket.aievent.core.task.lineage;

import java.util.List;

public interface TaskLineageRepository {
    TaskLineageEvidence append(TaskLineageEvidence evidence);
    List<TaskLineageEvidence> findByTask(String tenantId, String taskId, int limit);
    List<TaskLineageEvidence> findByRootTask(String tenantId, String rootTaskId, int limit);
    List<TaskLineageEvidence> findByAgent(String tenantId, String agentId, int limit);
    List<TaskLineageEvidence> findByCorrelation(String tenantId, String correlationId, int limit);
    List<TaskLineageEvidence> search(TaskLineageQuery query);
}
