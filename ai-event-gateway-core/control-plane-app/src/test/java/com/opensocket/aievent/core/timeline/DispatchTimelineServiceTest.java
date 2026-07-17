package com.opensocket.aievent.core.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.assignment.InMemoryTaskDispatchAttemptRepository;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskDispatchAttempt;
import com.opensocket.aievent.core.assignment.TaskDispatchAttemptStatus;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.executionattempt.InMemoryTaskExecutionAttemptRepository;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttempt;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttemptStatus;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

class DispatchTimelineServiceTest {

    @Test
    void shouldMergeDirectAssignmentExecutionAndCallbackIntoTimeline() {
        OffsetDateTime t0 = OffsetDateTime.of(2026, 6, 24, 4, 0, 0, 0, ZoneOffset.UTC);
        TaskRecord task = task("task-1", TaskStatus.RUNNING, t0);

        TaskOperationalQuery taskQuery = mock(TaskOperationalQuery.class);
        ExecutionOperationalQuery executionQuery = mock(ExecutionOperationalQuery.class);
        DispatchAttemptHistoryService historyService = mock(DispatchAttemptHistoryService.class);
        when(taskQuery.findTask("task-1")).thenReturn(Optional.of(task));
        when(taskQuery.findAssignmentsByTask(eq("task-1"), anyInt())).thenReturn(List.of(assignment(t0.plusSeconds(30))));
        when(executionQuery.findDispatchRequestsByTask(eq("task-1"), anyInt())).thenReturn(List.of());
        when(executionQuery.findCallbacksByTask(eq("task-1"), anyInt())).thenReturn(List.of(callback(t0.plusSeconds(90))));
        when(historyService.findByTaskId(eq("task-1"), anyInt())).thenReturn(List.of());

        InMemoryTaskDispatchAttemptRepository dispatchAttempts = new InMemoryTaskDispatchAttemptRepository();
        dispatchAttempts.save(dispatchAttempt(t0.plusSeconds(10)));
        InMemoryTaskExecutionAttemptRepository executionAttempts = new InMemoryTaskExecutionAttemptRepository();
        executionAttempts.save(executionAttempt(t0.plusSeconds(60)));

        DispatchTimelineService service = new DispatchTimelineService(
                taskQuery, executionQuery, historyService, dispatchAttempts, executionAttempts);

        DispatchTimelineResponse response = service.timeline("task-1", 100);

        assertThat(response.events()).extracting(DispatchTimelineEvent::action)
                .contains("TASK_CREATED", "DISPATCH_ATTEMPT_CREATED", "ASSIGNMENT_CREATED", "LEASE_GRANTED", "EXECUTION_ATTEMPT_CREATED", "EXECUTION_STARTED", "CALLBACK_RECEIVED");
        assertThat(response.events()).extracting(DispatchTimelineEvent::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, response.events().size()).boxed().toList());
    }

    @Test
    void failureQueueShouldIncludeDlqAndExposeManualRetryHint() {
        OffsetDateTime t0 = OffsetDateTime.of(2026, 6, 24, 5, 0, 0, 0, ZoneOffset.UTC);
        TaskRecord task = task("task-dlq", TaskStatus.DEAD_LETTER, t0);
        task.setTerminalAt(t0.plusMinutes(3));
        task.setLifecycleReason("max retry exceeded");

        TaskOperationalQuery taskQuery = mock(TaskOperationalQuery.class);
        ExecutionOperationalQuery executionQuery = mock(ExecutionOperationalQuery.class);
        DispatchAttemptHistoryService historyService = mock(DispatchAttemptHistoryService.class);
        when(taskQuery.searchTasks(org.mockito.ArgumentMatchers.any(TaskQuery.class))).thenAnswer(invocation -> {
            TaskQuery query = invocation.getArgument(0);
            return query.getStatus() == TaskStatus.DEAD_LETTER ? List.of(task) : List.of();
        });
        when(taskQuery.findAssignmentsByTask(eq("task-dlq"), anyInt())).thenReturn(List.of());
        when(executionQuery.findDispatchRequestsByTask(eq("task-dlq"), anyInt())).thenReturn(List.of());
        when(executionQuery.findCallbacksByTask(eq("task-dlq"), anyInt())).thenReturn(List.of());
        when(historyService.findByTaskId(eq("task-dlq"), anyInt())).thenReturn(List.of());

        DispatchTimelineService service = new DispatchTimelineService(
                taskQuery, executionQuery, historyService,
                new InMemoryTaskDispatchAttemptRepository(), new InMemoryTaskExecutionAttemptRepository());

        AdminFailureQueueResponse response = service.failureQueue(100);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().status()).isEqualTo(TaskStatus.DEAD_LETTER);
        assertThat(response.items().getFirst().actions()).containsEntry("manualRetry", true);
        assertThat(response.counts()).containsEntry("DEAD_LETTER", 1);
    }

    private TaskRecord task(String taskId, TaskStatus status, OffsetDateTime at) {
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setIncidentId("incident-1");
        task.setTaskType(TaskType.INCIDENT_RESPONSE);
        task.setStatus(status);
        task.setPriority(TaskPriority.HIGH);
        task.setTenantId("tenant-a");
        task.setSiteId("site-a");
        task.setCreatedAt(at);
        task.setUpdatedAt(at.plusMinutes(1));
        return task;
    }

    private TaskDispatchAttempt dispatchAttempt(OffsetDateTime at) {
        TaskDispatchAttempt attempt = new TaskDispatchAttempt();
        attempt.setDispatchAttemptId("dispatch-attempt-1");
        attempt.setTaskId("task-1");
        attempt.setIncidentId("incident-1");
        attempt.setSelectedAgentId("agent-1");
        attempt.setSelectedGatewayNodeId("gateway-1");
        attempt.setSelectedAgentSessionId("session-1");
        attempt.setStatus(TaskDispatchAttemptStatus.CREATED);
        attempt.setDecisionReason("selected best candidate");
        attempt.setCreatedAt(at);
        attempt.setUpdatedAt(at);
        return attempt;
    }


    private TaskAssignment assignment(OffsetDateTime at) {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assignment-1");
        assignment.setTaskId("task-1");
        assignment.setIncidentId("incident-1");
        assignment.setAgentId("agent-1");
        assignment.setOwnerGatewayNodeId("gateway-1");
        assignment.setAgentSessionId("session-1");
        assignment.setDispatchAttemptId("dispatch-attempt-1");
        assignment.setLeaseId("lease-1");
        assignment.setFencingToken("fence-1");
        assignment.setLeaseExpiresAt(at.plusMinutes(5));
        assignment.setCreatedAt(at);
        assignment.setUpdatedAt(at);
        return assignment;
    }

    private TaskExecutionAttempt executionAttempt(OffsetDateTime at) {
        TaskExecutionAttempt attempt = new TaskExecutionAttempt();
        attempt.setExecutionAttemptId("execution-1");
        attempt.setTaskId("task-1");
        attempt.setAssignmentId("assignment-1");
        attempt.setDispatchAttemptId("dispatch-attempt-1");
        attempt.setAgentId("agent-1");
        attempt.setAgentSessionId("session-1");
        attempt.setLeaseId("lease-1");
        attempt.setFencingToken("fence-1");
        attempt.setAttemptNo(1);
        attempt.setStatus(TaskExecutionAttemptStatus.RUNNING);
        attempt.setCreatedAt(at);
        attempt.setStartedAt(at.plusSeconds(5));
        attempt.setUpdatedAt(at.plusSeconds(5));
        return attempt;
    }

    private TaskCallbackRecord callback(OffsetDateTime at) {
        TaskCallbackRecord callback = new TaskCallbackRecord();
        callback.setCallbackId("callback-1");
        callback.setTaskId("task-1");
        callback.setAssignmentId("assignment-1");
        callback.setAgentId("agent-1");
        callback.setCallbackType(TaskCallbackType.PROGRESS);
        callback.setAccepted(true);
        callback.setMessage("50% complete");
        callback.setOccurredAt(at);
        callback.setProcessedAt(at.plusSeconds(1));
        return callback;
    }
}
