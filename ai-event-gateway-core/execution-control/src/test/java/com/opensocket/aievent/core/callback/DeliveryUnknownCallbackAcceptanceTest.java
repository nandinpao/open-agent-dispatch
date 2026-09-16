package com.opensocket.aievent.core.callback;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.dispatch.DispatchOutboxStatus;
import com.opensocket.aievent.core.dispatch.DispatchRecoveryClassification;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.InMemoryDispatchRequestRepository;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeliveryUnknownCallbackAcceptanceTest {

    @Test
    void acceptedAckMustConvergeResponseLostDispatchWithoutResend() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        InMemoryDispatchRequestRepository dispatches = new InMemoryDispatchRequestRepository();

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-pc-s2-callback");
        task.setIncidentId("incident-pc-s2-callback");
        task.setStatus(TaskStatus.RECONCILING);
        task.setCreatedAt(now.minusMinutes(1));
        task.setUpdatedAt(now.minusSeconds(10));
        tasks.save(task);

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-pc-s2-callback");
        dispatch.setTaskId(task.getTaskId());
        dispatch.setAssignmentId("assignment-pc-s2-callback");
        dispatch.setAgentId("agent-pc-s2");
        dispatch.setOwnerGatewayNodeId("gateway-pc-s2");
        dispatch.setAgentSessionId("session-pc-s2");
        dispatch.setStatus(DispatchRequestStatus.DELIVERY_UNKNOWN);
        dispatch.setOutboxStatus(DispatchOutboxStatus.RECOVERY_PENDING);
        dispatch.setRecoveryClassification(DispatchRecoveryClassification.RESPONSE_LOST);
        dispatch.setAttemptCount(1);
        dispatch.setDispatchToken("dispatch-token-pc-s2");
        dispatch.setUncertainSince(now.minusSeconds(10));
        dispatch.setCreatedAt(now.minusMinutes(1));
        dispatch.setUpdatedAt(now.minusSeconds(10));
        dispatches.save(dispatch);

        TaskCallbackProperties properties = new TaskCallbackProperties();
        properties.setEnforceAssignmentFencing(false);
        TaskCallbackService service = new TaskCallbackService(
                new InMemoryTaskCallbackRepository(), dispatches,
                new DefaultTaskOrchestrationFacade(null, null, tasks),
                properties, TaskTerminalActionPort.noop());

        TaskCallbackRequest callback = new TaskCallbackRequest();
        callback.setCallbackId("cb-pc-s2-ack");
        callback.setTaskId(task.getTaskId());
        callback.setDispatchRequestId(dispatch.getDispatchRequestId());
        callback.setAssignmentId(dispatch.getAssignmentId());
        callback.setAgentId(dispatch.getAgentId());
        callback.setOwnerGatewayNodeId(dispatch.getOwnerGatewayNodeId());
        callback.setAgentSessionId(dispatch.getAgentSessionId());
        callback.setAttemptNo(dispatch.getAttemptCount());
        callback.setDispatchToken(dispatch.getDispatchToken());
        callback.setMessage("late ACK proves original delivery");

        TaskCallbackResult result = service.ack(task.getTaskId(), callback);
        DispatchRequest converged = dispatches.findById(dispatch.getDispatchRequestId()).orElseThrow();

        assertThat(result.isAccepted()).isTrue();
        assertThat(converged.getStatus()).isEqualTo(DispatchRequestStatus.ACKED);
        assertThat(converged.getOutboxStatus()).isEqualTo(DispatchOutboxStatus.ACKNOWLEDGED);
        assertThat(converged.getRecoveryClassification()).isEqualTo(DispatchRecoveryClassification.NONE);
        assertThat(tasks.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.RUNNING);
    }
}
