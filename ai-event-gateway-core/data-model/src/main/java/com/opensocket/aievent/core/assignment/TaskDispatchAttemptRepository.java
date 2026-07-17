package com.opensocket.aievent.core.assignment;

import java.util.List;
import java.util.Optional;

public interface TaskDispatchAttemptRepository {
    TaskDispatchAttempt save(TaskDispatchAttempt attempt);
    Optional<TaskDispatchAttempt> findById(String dispatchAttemptId);
    List<TaskDispatchAttempt> findByTaskId(String taskId, int limit);
    List<TaskDispatchAttempt> recent(int limit);
    String mode();
}
