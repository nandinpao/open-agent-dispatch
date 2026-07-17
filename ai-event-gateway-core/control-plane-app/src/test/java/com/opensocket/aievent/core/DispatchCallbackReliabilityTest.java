package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.callback.DispatchRecoveryResult;
import com.opensocket.aievent.core.callback.DispatchRecoveryService;
import com.opensocket.aievent.core.callback.InMemoryTaskCallbackRepository;
import com.opensocket.aievent.core.callback.TaskCallbackErrorCode;
import com.opensocket.aievent.core.callback.TaskCallbackProperties;
import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackResult;
import com.opensocket.aievent.core.callback.TaskCallbackService;
import com.opensocket.aievent.core.callback.TaskTerminalActionPort;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.InMemoryDispatchRequestRepository;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

class DispatchCallbackReliabilityTest {
    @Test
    void shouldRejectOldAttemptCallback() {
        Fixture fixture = fixture();
        TaskCallbackRequest request = validCallback(fixture.dispatch);
        request.setAttemptNo(1);

        TaskCallbackResult result = fixture.service.result(fixture.task.getTaskId(), request);

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getIgnoredReason()).isEqualTo("OLD_ATTEMPT_CALLBACK");
        assertThat(result.getErrorCode()).isEqualTo(TaskCallbackErrorCode.OLD_ATTEMPT_CALLBACK.name());
        assertThat(result.getHttpStatus()).isEqualTo(409);
        assertThat(result.isRetryable()).isFalse();
        assertThat(fixture.dispatchRepository.findById(fixture.dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.DISPATCHED);
        assertThat(fixture.taskRepository.findById(fixture.task.getTaskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.DISPATCHED);
    }

    @Test
    void shouldRejectInvalidTransition() {
        Fixture fixture = fixture();
        TaskCallbackRequest request = validCallback(fixture.dispatch);

        TaskCallbackResult result = fixture.service.progress(fixture.task.getTaskId(), request);

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getIgnoredReason()).isEqualTo("INVALID_DISPATCH_TRANSITION_DISPATCHED_TO_PROGRESS");
        assertThat(result.getErrorCode()).isEqualTo(TaskCallbackErrorCode.INVALID_STATE_TRANSITION.name());
        assertThat(result.getHttpStatus()).isEqualTo(409);
    }

    @Test
    void shouldProcessAckThenResultWithStrictIdentityAndAttempt() {
        Fixture fixture = fixture();
        TaskCallbackRequest ack = validCallback(fixture.dispatch);

        TaskCallbackResult ackResult = fixture.service.ack(fixture.task.getTaskId(), ack);
        assertThat(ackResult.isAccepted()).isTrue();
        assertThat(fixture.dispatchRepository.findById(fixture.dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.ACKED);

        TaskCallbackRequest result = validCallback(fixture.dispatch);
        result.setCallbackId("cb-result-ok");
        result.setResultStatus("SUCCESS");
        TaskCallbackResult resultResponse = fixture.service.result(fixture.task.getTaskId(), result);

        assertThat(resultResponse.isAccepted()).isTrue();
        assertThat(fixture.dispatchRepository.findById(fixture.dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.COMPLETED);
        assertThat(fixture.taskRepository.findById(fixture.task.getTaskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.SUCCEEDED);
    }


    @Test
    void shouldNotAllowLateAckToOverwriteCompletedResult() {
        Fixture fixture = fixture();
        TaskCallbackRequest result = validCallback(fixture.dispatch);
        result.setCallbackId("cb-result-first");
        result.setResultStatus("SUCCESS");

        TaskCallbackResult resultResponse = fixture.service.result(fixture.task.getTaskId(), result);

        assertThat(resultResponse.isAccepted()).isTrue();
        assertThat(fixture.dispatchRepository.findById(fixture.dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.COMPLETED);
        assertThat(fixture.taskRepository.findById(fixture.task.getTaskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.SUCCEEDED);

        TaskCallbackRequest lateAck = validCallback(fixture.dispatch);
        lateAck.setCallbackId("cb-late-ack");

        TaskCallbackResult ackResponse = fixture.service.ack(fixture.task.getTaskId(), lateAck);

        assertThat(ackResponse.isAccepted()).isFalse();
        assertThat(ackResponse.getIgnoredReason()).isEqualTo("TASK_ALREADY_TERMINAL");
        assertThat(fixture.dispatchRepository.findById(fixture.dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.COMPLETED);
        assertThat(fixture.taskRepository.findById(fixture.task.getTaskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    void shouldNotAllowTimeoutRecoveryToOverwriteCompletedResult() {
        Fixture fixture = fixture();
        TaskCallbackRequest result = validCallback(fixture.dispatch);
        result.setCallbackId("cb-result-before-recovery");
        result.setResultStatus("SUCCESS");

        TaskCallbackResult resultResponse = fixture.service.result(fixture.task.getTaskId(), result);

        assertThat(resultResponse.isAccepted()).isTrue();

        DispatchRecoveryService recoveryService = new DispatchRecoveryService(
                fixture.dispatchRepository,
                new DefaultTaskOrchestrationFacade(null, null, fixture.taskRepository),
                new TaskCallbackProperties());
        DispatchRecoveryResult recovery = recoveryService.markTimedOut(fixture.dispatch.getDispatchRequestId());

        assertThat(recovery.isTimedOut()).isFalse();
        assertThat(fixture.dispatchRepository.findById(fixture.dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.COMPLETED);
        assertThat(fixture.taskRepository.findById(fixture.task.getTaskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.SUCCEEDED);
    }

    private Fixture fixture() {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();
        TaskCallbackProperties properties = new TaskCallbackProperties();
        TaskCallbackService service = new TaskCallbackService(
                new InMemoryTaskCallbackRepository(),
                dispatchRepository,
                new DefaultTaskOrchestrationFacade(null, null, taskRepository),
                properties,
                TaskTerminalActionPort.noop());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-1");
        task.setIncidentId("inc-1");
        task.setTaskType(TaskType.INCIDENT_RESPONSE);
        task.setPriority(TaskPriority.HIGH);
        task.setStatus(TaskStatus.DISPATCHED);
        task.setTenantId("tenant-a");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-1");
        dispatch.setAssignmentId("assignment-1");
        dispatch.setTaskId(task.getTaskId());
        dispatch.setIncidentId(task.getIncidentId());
        dispatch.setAgentId("agent-1");
        dispatch.setOwnerGatewayNodeId("gateway-1");
        dispatch.setAgentSessionId("session-1");
        dispatch.setDispatchToken("token-1");
        dispatch.setAttemptCount(2);
        dispatch.setStatus(DispatchRequestStatus.DISPATCHED);
        dispatch.setCreatedAt(now);
        dispatch.setUpdatedAt(now);
        dispatch.setDispatchedAt(now);
        dispatchRepository.save(dispatch);
        return new Fixture(service, taskRepository, dispatchRepository, task, dispatch);
    }

    private TaskCallbackRequest validCallback(DispatchRequest dispatch) {
        TaskCallbackRequest request = new TaskCallbackRequest();
        request.setCallbackId("cb-" + System.nanoTime());
        request.setDispatchRequestId(dispatch.getDispatchRequestId());
        request.setAgentId(dispatch.getAgentId());
        request.setOwnerGatewayNodeId(dispatch.getOwnerGatewayNodeId());
        request.setAgentSessionId(dispatch.getAgentSessionId());
        request.setAttemptNo(dispatch.getAttemptCount());
        request.setDispatchToken(dispatch.getDispatchToken());
        return request;
    }

    private record Fixture(TaskCallbackService service,
                           InMemoryTaskRepository taskRepository,
                           InMemoryDispatchRequestRepository dispatchRepository,
                           TaskRecord task,
                           DispatchRequest dispatch) {}
}
