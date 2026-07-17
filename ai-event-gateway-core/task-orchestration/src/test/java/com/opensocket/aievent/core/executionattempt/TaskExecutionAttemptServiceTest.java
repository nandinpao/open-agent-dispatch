package com.opensocket.aievent.core.executionattempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class TaskExecutionAttemptServiceTest {
    @Test
    void shouldCreateRunAndCompleteExecutionAttempt() {
        InMemoryTaskExecutionAttemptRepository repository = new InMemoryTaskExecutionAttemptRepository();
        TaskExecutionAttemptService service = new TaskExecutionAttemptService(repository);

        TaskExecutionAttempt attempt = service.createForAssignment(task(), assignment());
        assertThat(attempt.getStatus()).isEqualTo(TaskExecutionAttemptStatus.CREATED);
        assertThat(attempt.getAttemptNo()).isEqualTo(1);

        TaskExecutionAttempt running = service.markRunning(attempt.getExecutionAttemptId());
        assertThat(running.getStatus()).isEqualTo(TaskExecutionAttemptStatus.RUNNING);
        assertThat(running.getStartedAt()).isNotNull();

        TaskExecutionAttempt succeeded = service.markSucceeded(attempt.getExecutionAttemptId(), "cb-1", "OK");
        assertThat(succeeded.getStatus()).isEqualTo(TaskExecutionAttemptStatus.SUCCEEDED);
        assertThat(succeeded.getCallbackId()).isEqualTo("cb-1");
        assertThat(succeeded.getCompletedAt()).isNotNull();

        assertThatThrownBy(() -> service.markFailed(attempt.getExecutionAttemptId(), "cb-2", "ERR", "late failure"))
                .isInstanceOf(IllegalStateException.class);
    }

    private TaskRecord task() {
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-exec-1");
        task.setStatus(TaskStatus.ASSIGNED);
        return task;
    }

    private TaskAssignment assignment() {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-1");
        assignment.setTaskId("task-exec-1");
        assignment.setDispatchAttemptId("dispatch-attempt-1");
        assignment.setAgentId("agent-1");
        assignment.setAgentSessionId("session-1");
        assignment.setLeaseId("lease-1");
        return assignment;
    }
}
