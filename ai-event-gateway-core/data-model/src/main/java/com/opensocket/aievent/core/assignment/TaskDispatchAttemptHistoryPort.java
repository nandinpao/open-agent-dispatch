package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;

import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Optional cross-module audit port used by Task Orchestration to publish assignment/requeue
 * milestones without depending on the Execution Control module implementation.
 */
public interface TaskDispatchAttemptHistoryPort {
    void recordAssignmentCreated(TaskRecord task, TaskAssignment assignment, String reason, OffsetDateTime occurredAt);
    void recordAssignmentReused(TaskRecord task, TaskAssignment assignment, String reason, OffsetDateTime occurredAt);
    void recordDelayedDispatch(TaskRecord task, String routingDecisionId, String reason, OffsetDateTime nextAttemptAt, OffsetDateTime occurredAt);
    void recordRecoveryExhausted(TaskRecord task, String routingDecisionId, String reason, OffsetDateTime occurredAt);
    void recordRecoveryClaimed(TaskRecord task, String workerId, OffsetDateTime claimUntil, OffsetDateTime occurredAt);
    void recordRecoveryScannerFailed(TaskRecord task, String reason, OffsetDateTime nextAttemptAt, OffsetDateTime occurredAt);
    void recordTaskReassigned(TaskRecord task, String reason, OffsetDateTime occurredAt);

    static TaskDispatchAttemptHistoryPort noop() {
        return new TaskDispatchAttemptHistoryPort() {
            @Override public void recordAssignmentCreated(TaskRecord task, TaskAssignment assignment, String reason, OffsetDateTime occurredAt) {}
            @Override public void recordAssignmentReused(TaskRecord task, TaskAssignment assignment, String reason, OffsetDateTime occurredAt) {}
            @Override public void recordDelayedDispatch(TaskRecord task, String routingDecisionId, String reason, OffsetDateTime nextAttemptAt, OffsetDateTime occurredAt) {}
            @Override public void recordRecoveryExhausted(TaskRecord task, String routingDecisionId, String reason, OffsetDateTime occurredAt) {}
            @Override public void recordRecoveryClaimed(TaskRecord task, String workerId, OffsetDateTime claimUntil, OffsetDateTime occurredAt) {}
            @Override public void recordRecoveryScannerFailed(TaskRecord task, String reason, OffsetDateTime nextAttemptAt, OffsetDateTime occurredAt) {}
            @Override public void recordTaskReassigned(TaskRecord task, String reason, OffsetDateTime occurredAt) {}
        };
    }
}
