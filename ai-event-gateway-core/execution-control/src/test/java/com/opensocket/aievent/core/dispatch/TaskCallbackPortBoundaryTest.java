package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

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

class TaskCallbackPortBoundaryTest {
    @Test
    void terminalCallbackShouldInvokeActionPortOnceAndIgnoreDuplicate() {
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        DefaultTaskOrchestrationFacade taskFacade = new DefaultTaskOrchestrationFacade(null, null, taskRepository);
        AtomicInteger terminalInvocations = new AtomicInteger();
        TaskTerminalActionPort terminalPort = (task, dispatch, callback, type) -> terminalInvocations.incrementAndGet();
        TaskCallbackService service = new TaskCallbackService(
                new InMemoryTaskCallbackRepository(),
                dispatchRepository,
                taskFacade,
                new TaskCallbackProperties(),
                terminalPort);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-m4-callback");
        task.setStatus(TaskStatus.DISPATCHED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-m4-callback");
        dispatch.setTaskId(task.getTaskId());
        dispatch.setStatus(DispatchRequestStatus.DISPATCHED);
        dispatch.setDispatchToken("token-m4");
        dispatch.setAttemptCount(1);
        dispatch.setAgentId("agent-m4");
        dispatch.setOwnerGatewayNodeId("gateway-m4");
        dispatch.setAgentSessionId("session-m4");
        dispatch.setCreatedAt(now);
        dispatch.setUpdatedAt(now);
        dispatchRepository.save(dispatch);

        TaskCallbackRequest callback = new TaskCallbackRequest();
        callback.setCallbackId("callback-m4-terminal");
        callback.setDispatchRequestId(dispatch.getDispatchRequestId());
        callback.setDispatchToken(dispatch.getDispatchToken());
        callback.setAttemptNo(dispatch.getAttemptCount());
        callback.setAgentId(dispatch.getAgentId());
        callback.setOwnerGatewayNodeId(dispatch.getOwnerGatewayNodeId());
        callback.setAgentSessionId(dispatch.getAgentSessionId());
        callback.setResultStatus("SUCCESS");

        TaskCallbackResult first = service.result(task.getTaskId(), callback);
        TaskCallbackResult duplicate = service.result(task.getTaskId(), callback);

        assertThat(first.isAccepted()).isTrue();
        assertThat(duplicate.isDuplicate()).isTrue();
        assertThat(terminalInvocations.get()).isEqualTo(1);
        assertThat(taskRepository.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(dispatchRepository.findById(dispatch.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.COMPLETED);
    }
}
