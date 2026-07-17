package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class ExecutionControlRetryTest {
    @Test
    void failedNettyDispatchShouldMoveRequestToRetryWaitingThroughOutboundPort() {
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        DefaultTaskOrchestrationFacade taskFacade = new DefaultTaskOrchestrationFacade(null, null, taskRepository);
        DispatchProperties properties = new DispatchProperties();
        properties.getRetry().setEnabled(true);
        properties.getRetry().setMaxAttempts(3);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-m4-1");
        task.setStatus(TaskStatus.ASSIGNED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.save(task);

        DispatchRequest request = new DispatchRequest();
        request.setDispatchRequestId("dispatch-m4-1");
        request.setTaskId(task.getTaskId());
        request.setStatus(DispatchRequestStatus.APPROVED);
        request.setAttemptCount(0);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        dispatchRepository.save(request);

        NettyDispatchPort netty = ignored -> GatewayDispatchResult.failure(503, "GATEWAY_UNAVAILABLE", "gateway unavailable");
        DispatchExecutionService service = new DispatchExecutionService(dispatchRepository, taskFacade, netty, properties);

        DispatchExecutionResult result = service.execute(request.getDispatchRequestId());

        assertThat(result.isExecuted()).isFalse();
        assertThat(dispatchRepository.findById(request.getDispatchRequestId()).orElseThrow().getStatus())
                .isEqualTo(DispatchRequestStatus.RETRY_WAITING);
        assertThat(dispatchRepository.findById(request.getDispatchRequestId()).orElseThrow().getNextRetryAt())
                .isNotNull();
        TaskRecord retryWaitingTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(retryWaitingTask.getStatus()).isEqualTo(TaskStatus.RETRY_WAIT);
        assertThat(retryWaitingTask.getNextDispatchAttemptAt())
                .isEqualTo(dispatchRepository.findById(request.getDispatchRequestId()).orElseThrow().getNextRetryAt());
        assertThat(retryWaitingTask.getDispatchRetryReason()).contains("Netty dispatch retry scheduled");
        assertThat(result.getTaskStatus()).isEqualTo(TaskStatus.RETRY_WAIT.name());
    }
}
