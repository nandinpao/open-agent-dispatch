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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeliveryUnknownTimeoutRecoveryTest {

    @Test
    void unresolvedUnknownDeliveryMustTimeOutWithoutAutomaticRetry() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        InMemoryDispatchRequestRepository dispatches = new InMemoryDispatchRequestRepository();

        TaskRecord task = new TaskRecord();
        task.setTaskId("task-pc-s2-timeout");
        task.setIncidentId("incident-pc-s2-timeout");
        task.setStatus(TaskStatus.RECONCILING);
        task.setCreatedAt(now.minusMinutes(5));
        task.setUpdatedAt(now.minusMinutes(3));
        tasks.save(task);

        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId("dispatch-pc-s2-timeout");
        dispatch.setTaskId(task.getTaskId());
        dispatch.setStatus(DispatchRequestStatus.DELIVERY_UNKNOWN);
        dispatch.setOutboxStatus(DispatchOutboxStatus.RECOVERY_PENDING);
        dispatch.setRecoveryClassification(DispatchRecoveryClassification.RESPONSE_LOST);
        dispatch.setAttemptCount(1);
        dispatch.setCreatedAt(now.minusMinutes(5));
        dispatch.setUpdatedAt(now.minusMinutes(3));
        dispatch.setUncertainSince(now.minusMinutes(3));
        dispatches.save(dispatch);

        TaskCallbackProperties properties = new TaskCallbackProperties();
        properties.getRecovery().setDispatchTimeout(Duration.ofSeconds(1));
        properties.getRecovery().setRetryEnabled(true);
        properties.getRecovery().setAutoFailTimedOut(true);
        DispatchRecoveryService recovery = new DispatchRecoveryService(
                dispatches, new DefaultTaskOrchestrationFacade(null, null, tasks), properties);

        var results = recovery.scanAndRecoverTimedOut(10);
        DispatchRequest timedOut = dispatches.findById(dispatch.getDispatchRequestId()).orElseThrow();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().isRetryScheduled()).isFalse();
        assertThat(timedOut.getStatus()).isEqualTo(DispatchRequestStatus.TIMED_OUT);
        assertThat(timedOut.getNextRetryAt()).isNull();
        assertThat(tasks.findById(task.getTaskId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.ORPHANED);
    }
}
