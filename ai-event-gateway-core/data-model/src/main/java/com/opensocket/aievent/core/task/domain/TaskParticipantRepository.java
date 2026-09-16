package com.opensocket.aievent.core.task.domain;

import java.util.List;
import java.util.Optional;

public interface TaskParticipantRepository {
    TaskParticipant save(TaskParticipant participant);
    Optional<TaskParticipant> findById(String tenantId, String participantId);
    Optional<TaskParticipant> findNatural(String tenantId, String taskId, TaskParticipantType participantType, String participantRefId, TaskParticipantRole participantRole);
    List<TaskParticipant> findByTask(String tenantId, String taskId, int limit);
    boolean delete(String tenantId, String participantId);
    String mode();
}
