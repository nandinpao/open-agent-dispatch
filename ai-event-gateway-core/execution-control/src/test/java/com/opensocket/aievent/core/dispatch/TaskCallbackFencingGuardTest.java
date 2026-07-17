package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.opensocket.aievent.core.assignment.AssignmentFencingTokenPolicy;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.InMemoryTaskAssignmentRepository;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.callback.InMemoryTaskCallbackRepository;
import com.opensocket.aievent.core.callback.TaskCallbackProperties;
import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackResult;
import com.opensocket.aievent.core.callback.TaskCallbackService;
import com.opensocket.aievent.core.callback.TaskTerminalActionPort;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class TaskCallbackFencingGuardTest {
    @Test
    void shouldRejectCallbackWithStaleAssignmentFenceBeforeMutatingTask() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();
        InMemoryTaskAssignmentRepository assignmentRepository = new InMemoryTaskAssignmentRepository();

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-fence-1");
        task.setIncidentId("inc-fence-1");
        task.setStatus(TaskStatus.ASSIGNED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);

        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-fence-1");
        assignment.setTaskId(task.getTaskId());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAgentId("agent-1");
        assignment.setOwnerGatewayNodeId("gateway-1");
        assignment.setAgentSessionId("session-1");
        assignment.setLeaseId("lease-1");
        assignment.setFencingToken("fence-current");
        assignment.setLeaseExpiresAt(now.plusMinutes(5));
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        assignmentRepository.save(assignment);

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-fence-1");
        dispatch.setTaskId(task.getTaskId());
        dispatch.setAssignmentId(assignment.getAssignmentId());
        dispatch.setAgentId("agent-1");
        dispatch.setOwnerGatewayNodeId("gateway-1");
        dispatch.setAgentSessionId("session-1");
        dispatch.setStatus(DispatchRequestStatus.DISPATCHED);
        dispatch.setAttemptCount(1);
        dispatch.setDispatchToken("dispatch-token-1");
        dispatch.setCreatedAt(now);
        dispatch.setUpdatedAt(now);
        dispatchRepository.save(dispatch);

        TaskCallbackProperties properties = new TaskCallbackProperties();
        properties.setRequireKnownAssignmentForFencing(true);
        TaskCallbackService service = new TaskCallbackService(
                new InMemoryTaskCallbackRepository(),
                dispatchRepository,
                new DefaultTaskOrchestrationFacade(null, null, taskRepository),
                properties,
                TaskTerminalActionPort.noop());
        ReflectionTestUtils.setField(service, "assignmentRepository", assignmentRepository);
        ReflectionTestUtils.setField(service, "assignmentFencingTokenPolicy", new AssignmentFencingTokenPolicy());

        TaskCallbackRequest callback = new TaskCallbackRequest();
        callback.setCallbackId("cb-stale-fence");
        callback.setTaskId(task.getTaskId());
        callback.setDispatchRequestId(dispatch.getDispatchRequestId());
        callback.setAssignmentId(assignment.getAssignmentId());
        callback.setAgentId("agent-1");
        callback.setOwnerGatewayNodeId("gateway-1");
        callback.setAgentSessionId("session-1");
        callback.setAttemptNo(1);
        callback.setDispatchToken("dispatch-token-1");
        callback.setFencingToken("fence-old");
        callback.setResultStatus("SUCCESS");

        TaskCallbackResult result = service.result(task.getTaskId(), callback);

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getIgnoredReason()).isEqualTo("INVALID_FENCING_TOKEN");
        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(dispatchRepository.findById(dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.DISPATCHED);
    }
}
