package com.opensocket.aievent.core.executionattempt;

import java.util.List;
import java.util.Optional;

public interface TaskExecutionAttemptRepository {
    TaskExecutionAttempt save(TaskExecutionAttempt attempt);
    Optional<TaskExecutionAttempt> findById(String executionAttemptId);
    Optional<TaskExecutionAttempt> findCurrentByAssignmentId(String assignmentId);
    List<TaskExecutionAttempt> findByTaskId(String taskId, int limit);
    int countByTaskId(String taskId);
    String mode();
}
