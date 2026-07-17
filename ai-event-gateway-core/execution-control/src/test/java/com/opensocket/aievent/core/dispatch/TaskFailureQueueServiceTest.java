package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class TaskFailureQueueServiceTest {
    @Test
    void manualRetryIsIdempotentAndDoesNotMoveRetryTimestamp() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        DefaultTaskOrchestrationFacade facade = new DefaultTaskOrchestrationFacade(null, null, repository);
        TaskFailureQueueService service = new TaskFailureQueueService(facade);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-dlq-1");
        task.setIncidentId("inc-dlq-1");
        task.setStatus(TaskStatus.FAILED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        repository.save(task);

        assertThat(service.deadLetter(task.getTaskId(), "max retry", now).getStatus()).isEqualTo(TaskStatus.DEAD_LETTER);
        TaskRecord firstRetry = service.manualRetry(task.getTaskId(), "operator retry", now.plusSeconds(1));
        OffsetDateTime firstRetryAt = firstRetry.getNextDispatchAttemptAt();
        TaskRecord duplicateRetry = service.manualRetry(task.getTaskId(), "operator retry", now.plusSeconds(2));
        assertThat(firstRetry.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(duplicateRetry.getNextDispatchAttemptAt()).isEqualTo(firstRetryAt);
        assertThat(repository.findById(task.getTaskId()).orElseThrow().getNextDispatchAttemptAt()).isEqualTo(firstRetryAt);
        assertThat(service.escalate(task.getTaskId(), "needs review", now.plusSeconds(2)).getStatus()).isEqualTo(TaskStatus.ESCALATED);
    }
}
