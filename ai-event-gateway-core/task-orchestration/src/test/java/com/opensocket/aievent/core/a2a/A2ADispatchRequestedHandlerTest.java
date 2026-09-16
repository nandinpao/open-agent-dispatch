package com.opensocket.aievent.core.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.opensocket.aievent.core.a2a.application.port.in.A2AGovernanceUseCase;
import com.opensocket.aievent.core.a2a.authority.A2ADispatchRequestedHandler;
import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.events.A2ADispatchRequestedEvent;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;

class A2ADispatchRequestedHandlerTest {

    @Test
    void noAssignmentMustBlockA2AInsteadOfSilentlyAcknowledgingDispatchIntent() {
        TaskRepository tasks = mock(TaskRepository.class);
        TaskAssignmentService assignments = mock(TaskAssignmentService.class);
        A2AGovernanceUseCase governance = mock(A2AGovernanceUseCase.class);
        A2ADispatchRequestedHandler handler = new A2ADispatchRequestedHandler(tasks, assignments, governance);

        TaskRecord child = new TaskRecord();
        child.setTaskId("task-child");
        child.setTenantId("tenant-a");
        child.setStatus(TaskStatus.QUEUED);
        child.setTargetPoolId("pool-mes");
        child.setAssignedPoolId("pool-mes");
        when(tasks.findByTenantAndId("tenant-a", "task-child")).thenReturn(Optional.of(child));
        when(assignments.assignIfPossible(child)).thenReturn(AssignmentDecisionResult.withDispatch(
                false, null, null, null, null, null,
                "route-1", "NO_CANDIDATE", "POOL_HAS_NO_ACTIVE_MEMBER",
                com.opensocket.aievent.core.dispatch.DispatchDecisionResult.none("No assignment was created")));

        A2ADispatchRequestedEvent event = new A2ADispatchRequestedEvent(
                "evt-1", "tenant-a", "a2ar-1", "task-child", "pool-mes",
                "a2a-dispatch:a2ar-1", "corr-1", OffsetDateTime.parse("2026-08-20T01:00:00Z"));

        handler.handle(event);

        ArgumentCaptor<A2ADispatchProgressCommand> captor = ArgumentCaptor.forClass(A2ADispatchProgressCommand.class);
        verify(governance).recordDispatchProgress(captor.capture());
        assertThat(captor.getValue().stage()).isEqualTo("FAILED_RETRYABLE");
        assertThat(captor.getValue().blockerCode()).isEqualTo(A2ABlockerCode.NO_ELIGIBLE_AGENT);
        assertThat(captor.getValue().childTaskId()).isEqualTo("task-child");
    }

    @Test
    void successfulAssignmentAndDispatchRequestMustNotCreateSyntheticBlocker() {
        TaskRepository tasks = mock(TaskRepository.class);
        TaskAssignmentService assignments = mock(TaskAssignmentService.class);
        A2AGovernanceUseCase governance = mock(A2AGovernanceUseCase.class);
        A2ADispatchRequestedHandler handler = new A2ADispatchRequestedHandler(tasks, assignments, governance);

        TaskRecord child = new TaskRecord();
        child.setTaskId("task-child");
        child.setTenantId("tenant-a");
        child.setStatus(TaskStatus.QUEUED);
        child.setTargetPoolId("pool-mes");
        when(tasks.findByTenantAndId("tenant-a", "task-child")).thenReturn(Optional.of(child));
        when(assignments.assignIfPossible(child)).thenReturn(new AssignmentDecisionResult(
                true, "assign-1", "agent-mes", "gw-1", "session-1", "LOCAL",
                "route-1", "ASSIGNED", "selected",
                true, "dispatch-1", "APPROVED", "NOT_REQUIRED", "ELIGIBLE", "/dispatch", "queued"));

        handler.handle(new A2ADispatchRequestedEvent(
                "evt-2", "tenant-a", "a2ar-2", "task-child", "pool-mes",
                "a2a-dispatch:a2ar-2", "corr-2", OffsetDateTime.parse("2026-08-20T01:00:00Z")));

        verify(governance, org.mockito.Mockito.never()).recordDispatchProgress(any());
    }
}
