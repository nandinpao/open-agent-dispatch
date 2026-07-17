package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

/**
 * TODO 15-D admin failure queue primitives: escalation, dead-letter, and manual retry.
 */
@Service
public class TaskFailureQueueService {
    private final TaskOrchestrationFacade taskOrchestrationFacade;

    public TaskFailureQueueService(TaskOrchestrationFacade taskOrchestrationFacade) {
        this.taskOrchestrationFacade = taskOrchestrationFacade;
    }

    @Transactional
    public TaskRecord deadLetter(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task = requireTask(taskId);
        OffsetDateTime at = effectiveNow(now);
        task.setStatus(TaskStatus.DEAD_LETTER);
        task.setTerminalAt(at);
        task.setUpdatedAt(at);
        task.setLifecycleReason(firstNonBlank(reason, "Moved to dead letter queue"));
        return taskOrchestrationFacade.saveExecutionState(task);
    }

    @Transactional
    public TaskRecord escalate(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task = requireTask(taskId);
        OffsetDateTime at = effectiveNow(now);
        task.setStatus(TaskStatus.ESCALATED);
        task.setTerminalAt(at);
        task.setUpdatedAt(at);
        task.setLifecycleReason(firstNonBlank(reason, "Escalated for operator review"));
        return taskOrchestrationFacade.saveExecutionState(task);
    }

    @Transactional
    public TaskRecord manualRetry(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task = requireTask(taskId);
        OffsetDateTime at = effectiveNow(now);
        String retryReason = firstNonBlank(reason, "Manual retry requested");
        if (task.getStatus() == TaskStatus.QUEUED
                && task.getNextDispatchAttemptAt() != null
                && retryReason.equals(task.getDispatchRetryReason())) {
            return task;
        }
        task.setStatus(TaskStatus.QUEUED);
        task.setTerminalAt(null);
        task.setTimeoutAt(null);
        task.setNextDispatchAttemptAt(at);
        task.setDispatchRetryReason(retryReason);
        task.setDispatchAttemptCount(0);
        task.setUpdatedAt(at);
        task.setLifecycleReason(retryReason);
        return taskOrchestrationFacade.saveExecutionState(task);
    }

    @Transactional
    public TaskRecord applyDecision(String taskId, RedispatchDecision decision, OffsetDateTime now) {
        if (decision == null) {
            return requireTask(taskId);
        }
        OffsetDateTime at = effectiveNow(now);
        return switch (decision.action()) {
            case IMMEDIATE_REDISPATCH -> manualRetry(taskId, decision.reason(), at);
            case RETRY_WAIT -> retryWait(taskId, decision, at);
            case FAILED -> fail(taskId, decision.reason(), at);
            case ESCALATED -> escalate(taskId, decision.reason(), at);
            case DEAD_LETTER -> deadLetter(taskId, decision.reason(), at);
        };
    }

    @Transactional
    public TaskRecord retryWait(String taskId, RedispatchDecision decision, OffsetDateTime now) {
        TaskRecord task = requireTask(taskId);
        OffsetDateTime at = effectiveNow(now);
        task.setStatus(TaskStatus.RETRY_WAIT);
        task.setNextDispatchAttemptAt(decision == null || decision.nextRetryAt() == null ? at : decision.nextRetryAt());
        task.setDispatchAttemptCount(decision == null ? task.getDispatchAttemptCount() + 1 : decision.nextAttemptNo());
        task.setDispatchRetryReason(decision == null ? "Retry wait scheduled" : decision.reason());
        task.setUpdatedAt(at);
        task.setLifecycleReason(task.getDispatchRetryReason());
        return taskOrchestrationFacade.saveExecutionState(task);
    }

    @Transactional
    public TaskRecord fail(String taskId, String reason, OffsetDateTime now) {
        TaskRecord task = requireTask(taskId);
        OffsetDateTime at = effectiveNow(now);
        task.setStatus(TaskStatus.FAILED);
        task.setTerminalAt(at);
        task.setUpdatedAt(at);
        task.setLifecycleReason(firstNonBlank(reason, "Task failed"));
        return taskOrchestrationFacade.saveExecutionState(task);
    }

    private TaskRecord requireTask(String taskId) {
        return taskOrchestrationFacade.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private OffsetDateTime effectiveNow(OffsetDateTime now) {
        return now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
