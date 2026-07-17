package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

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

class DispatchReliabilityP2Test {

    @Test
    void shouldRejectSameCallbackIdWithDifferentPayloadFingerprintAsReplay() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-p2-replay");
        task.setIncidentId("incident-p2-replay");
        task.setStatus(TaskStatus.ASSIGNED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-p2-replay");
        dispatch.setTaskId(task.getTaskId());
        dispatch.setAssignmentId("assignment-p2-replay");
        dispatch.setAgentId("agent-p2");
        dispatch.setOwnerGatewayNodeId("gateway-p2");
        dispatch.setAgentSessionId("session-p2");
        dispatch.setStatus(DispatchRequestStatus.DISPATCHED);
        dispatch.setAttemptCount(1);
        dispatch.setDispatchToken("dispatch-token-p2");
        dispatch.setCreatedAt(now);
        dispatch.setUpdatedAt(now);
        dispatchRepository.save(dispatch);

        TaskCallbackProperties properties = new TaskCallbackProperties();
        properties.setReplayProtectionEnabled(true);
        properties.setRejectCallbackIdReplayMismatch(true);
        properties.setEnforceAssignmentFencing(false);
        TaskCallbackService service = new TaskCallbackService(
                new InMemoryTaskCallbackRepository(),
                dispatchRepository,
                new DefaultTaskOrchestrationFacade(null, null, taskRepository),
                properties,
                TaskTerminalActionPort.noop());

        TaskCallbackRequest first = callback(dispatch, "cb-p2-replay", "SUCCESS", "first payload");
        TaskCallbackResult accepted = service.result(task.getTaskId(), first);
        assertThat(accepted.isAccepted()).isTrue();

        TaskCallbackRequest replay = callback(dispatch, "cb-p2-replay", "SUCCESS", "tampered payload");
        TaskCallbackResult rejected = service.result(task.getTaskId(), replay);

        assertThat(rejected.isDuplicate()).isTrue();
        assertThat(rejected.isAccepted()).isFalse();
        assertThat(rejected.getIgnoredReason()).isEqualTo("CALLBACK_REPLAY_MISMATCH");
        assertThat(rejected.getErrorCode()).isEqualTo("CALLBACK_REPLAY_MISMATCH");
        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(dispatchRepository.findById(dispatch.getDispatchRequestId()).orElseThrow().getStatus()).isEqualTo(DispatchRequestStatus.COMPLETED);
    }

    @Test
    void retryBackoffShouldUseBoundedDeterministicJitterWhenStableKeyIsProvided() {
        DispatchProperties properties = new DispatchProperties();
        properties.getRetry().setInitialBackoff(Duration.ofSeconds(100));
        properties.getRetry().setMaxBackoff(Duration.ofSeconds(500));
        properties.getRetry().setJitterPercent(20);
        TaskRetryBackoffPolicy policy = new TaskRetryBackoffPolicy(properties);

        Duration base = policy.delayForAttempt(1);
        Duration jitteredA = policy.delayForAttempt(1, "dispatch-a");
        Duration jitteredARepeat = policy.delayForAttempt(1, "dispatch-a");

        assertThat(base).isEqualTo(Duration.ofSeconds(100));
        assertThat(jitteredA).isEqualTo(jitteredARepeat);
        assertThat(jitteredA.toMillis()).isBetween(80_000L, 120_000L);
    }


    @Test
    void terminalTaskCallbackShouldBeRejectedWithoutOverridingTerminalState() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-p2-terminal");
        task.setIncidentId("incident-p2-terminal");
        task.setStatus(TaskStatus.SUCCEEDED);
        task.setTerminalAt(now.minusSeconds(10));
        task.setCreatedAt(now.minusMinutes(1));
        task.setUpdatedAt(now.minusSeconds(10));
        taskRepository.save(task);

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-p2-terminal");
        dispatch.setTaskId(task.getTaskId());
        dispatch.setAssignmentId("assignment-p2-terminal");
        dispatch.setAgentId("agent-p2");
        dispatch.setOwnerGatewayNodeId("gateway-p2");
        dispatch.setAgentSessionId("session-p2");
        dispatch.setStatus(DispatchRequestStatus.COMPLETED);
        dispatch.setAttemptCount(1);
        dispatch.setDispatchToken("dispatch-token-terminal");
        dispatch.setCreatedAt(now.minusMinutes(1));
        dispatch.setUpdatedAt(now.minusSeconds(10));
        dispatchRepository.save(dispatch);

        TaskCallbackProperties properties = new TaskCallbackProperties();
        properties.setEnforceAssignmentFencing(false);
        properties.setAllowTerminalCallbackOverride(false);
        TaskCallbackService service = new TaskCallbackService(
                new InMemoryTaskCallbackRepository(),
                dispatchRepository,
                new DefaultTaskOrchestrationFacade(null, null, taskRepository),
                properties,
                TaskTerminalActionPort.noop());

        TaskCallbackRequest late = callback(dispatch, "cb-p2-late-terminal", "FAILED", "late failure should not override success");
        late.setErrorCode("LATE_ERROR");
        TaskCallbackResult rejected = service.error(task.getTaskId(), late);

        assertThat(rejected.isAccepted()).isFalse();
        assertThat(rejected.getIgnoredReason()).isEqualTo("TASK_ALREADY_TERMINAL");
        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(dispatchRepository.findById(dispatch.getDispatchRequestId()).orElseThrow().getStatus()).isEqualTo(DispatchRequestStatus.COMPLETED);
    }

    private TaskCallbackRequest callback(DispatchRequest dispatch, String callbackId, String resultStatus, String message) {
        TaskCallbackRequest callback = new TaskCallbackRequest();
        callback.setCallbackId(callbackId);
        callback.setTaskId(dispatch.getTaskId());
        callback.setDispatchRequestId(dispatch.getDispatchRequestId());
        callback.setAssignmentId(dispatch.getAssignmentId());
        callback.setAgentId(dispatch.getAgentId());
        callback.setOwnerGatewayNodeId(dispatch.getOwnerGatewayNodeId());
        callback.setAgentSessionId(dispatch.getAgentSessionId());
        callback.setAttemptNo(dispatch.getAttemptCount());
        callback.setDispatchToken(dispatch.getDispatchToken());
        callback.setResultStatus(resultStatus);
        callback.setMessage(message);
        return callback;
    }
}
