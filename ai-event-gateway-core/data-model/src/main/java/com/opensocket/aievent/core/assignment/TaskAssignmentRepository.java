package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskAssignmentRepository {
    TaskAssignment save(TaskAssignment assignment);
    Optional<TaskAssignment> findById(String assignmentId);
    Optional<TaskAssignment> findOpenByTaskId(String taskId);
    boolean releaseCapacityReservation(String assignmentId, OffsetDateTime releasedAt);
    List<TaskAssignment> findByTaskId(String taskId, int limit);
    List<TaskAssignment> recent(int limit);
    String mode();
}
