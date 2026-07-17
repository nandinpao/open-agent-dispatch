package com.opensocket.aievent.core.executionattempt;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.task.TaskRecord;

@Service
public class TaskExecutionAttemptService {
    private final TaskExecutionAttemptRepository repository;

    public TaskExecutionAttemptService(TaskExecutionAttemptRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskExecutionAttempt createForAssignment(TaskRecord task, TaskAssignment assignment) {
        if (task == null || assignment == null) {
            throw new IllegalArgumentException("task and assignment are required");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskExecutionAttempt attempt = new TaskExecutionAttempt();
        attempt.setExecutionAttemptId("exec-attempt-" + UUID.randomUUID());
        attempt.setTaskId(task.getTaskId());
        attempt.setAssignmentId(assignment.getAssignmentId());
        attempt.setDispatchAttemptId(assignment.getDispatchAttemptId());
        attempt.setAgentId(assignment.getAgentId());
        attempt.setAgentSessionId(assignment.getAgentSessionId());
        attempt.setLeaseId(assignment.getLeaseId());
        attempt.setFencingToken(assignment.getFencingToken());
        attempt.setAttemptNo(repository.countByTaskId(task.getTaskId()) + 1);
        attempt.setStatus(TaskExecutionAttemptStatus.CREATED);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        return repository.save(attempt);
    }

    @Transactional
    public TaskExecutionAttempt markRunning(String executionAttemptId) {
        TaskExecutionAttempt attempt = repository.findById(executionAttemptId)
                .orElseThrow(() -> new IllegalArgumentException("Execution attempt not found: " + executionAttemptId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (attempt.getStatus() != TaskExecutionAttemptStatus.CREATED) {
            throw new IllegalStateException("Execution attempt cannot start from status " + attempt.getStatus());
        }
        attempt.setStatus(TaskExecutionAttemptStatus.RUNNING);
        attempt.setStartedAt(now);
        attempt.setUpdatedAt(now);
        return repository.save(attempt);
    }


    @Transactional
    public Optional<TaskExecutionAttempt> markRunningForAssignment(String assignmentId) {
        return repository.findCurrentByAssignmentId(assignmentId)
                .map(attempt -> {
                    if (attempt.getStatus() == TaskExecutionAttemptStatus.RUNNING) {
                        return attempt;
                    }
                    return markRunning(attempt.getExecutionAttemptId());
                });
    }

    @Transactional
    public Optional<TaskExecutionAttempt> markSucceededForAssignment(String assignmentId, String callbackId, String resultCode) {
        return repository.findCurrentByAssignmentId(assignmentId)
                .map(attempt -> markSucceeded(attempt.getExecutionAttemptId(), callbackId, resultCode));
    }

    @Transactional
    public Optional<TaskExecutionAttempt> markFailedForAssignment(String assignmentId, String callbackId, String errorCode, String errorMessage) {
        return repository.findCurrentByAssignmentId(assignmentId)
                .map(attempt -> markFailed(attempt.getExecutionAttemptId(), callbackId, errorCode, errorMessage));
    }

    @Transactional
    public Optional<TaskExecutionAttempt> markStaleCallbackRejected(String assignmentId, String callbackId, String reason) {
        return repository.findCurrentByAssignmentId(assignmentId)
                .map(attempt -> {
                    if (attempt.getStatus() == TaskExecutionAttemptStatus.CREATED
                            || attempt.getStatus() == TaskExecutionAttemptStatus.RUNNING) {
                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                        attempt.setStatus(TaskExecutionAttemptStatus.STALE_CALLBACK_REJECTED);
                        attempt.setCallbackId(callbackId);
                        attempt.setErrorCode("STALE_CALLBACK_REJECTED");
                        attempt.setErrorMessage(reason);
                        attempt.setCompletedAt(now);
                        attempt.setUpdatedAt(now);
                        return repository.save(attempt);
                    }
                    return attempt;
                });
    }

    @Transactional
    public TaskExecutionAttempt markSucceeded(String executionAttemptId, String callbackId, String resultCode) {
        return complete(executionAttemptId, TaskExecutionAttemptStatus.SUCCEEDED, callbackId, resultCode, null, null);
    }

    @Transactional
    public TaskExecutionAttempt markFailed(String executionAttemptId, String callbackId, String errorCode, String errorMessage) {
        return complete(executionAttemptId, TaskExecutionAttemptStatus.FAILED, callbackId, null, errorCode, errorMessage);
    }

    private TaskExecutionAttempt complete(String executionAttemptId,
                                          TaskExecutionAttemptStatus status,
                                          String callbackId,
                                          String resultCode,
                                          String errorCode,
                                          String errorMessage) {
        TaskExecutionAttempt attempt = repository.findById(executionAttemptId)
                .orElseThrow(() -> new IllegalArgumentException("Execution attempt not found: " + executionAttemptId));
        if (attempt.getStatus() != TaskExecutionAttemptStatus.CREATED
                && attempt.getStatus() != TaskExecutionAttemptStatus.RUNNING) {
            throw new IllegalStateException("Execution attempt is already terminal: " + attempt.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        attempt.setStatus(status);
        attempt.setCallbackId(callbackId);
        attempt.setResultCode(resultCode);
        attempt.setErrorCode(errorCode);
        attempt.setErrorMessage(errorMessage);
        attempt.setCompletedAt(now);
        attempt.setUpdatedAt(now);
        return repository.save(attempt);
    }
}
