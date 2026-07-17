package com.opensocket.aievent.core.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.events.DispatchDeadLetteredEvent;
import com.opensocket.aievent.core.events.ModuleEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.outbox.OutboxEventRecord;
import com.opensocket.aievent.core.task.DefaultTaskOrchestrationFacade;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

class DispatchDeadLetterEventTest {

    @Test
    void retryWaitingMustNotEmitDeadLetterEvent() {
        Fixture fixture = fixture(3);

        fixture.service.execute(fixture.request.getDispatchRequestId());

        assertEquals(DispatchRequestStatus.RETRY_WAITING,
                fixture.repository.findById(fixture.request.getDispatchRequestId()).orElseThrow().getStatus());
        assertEquals(TaskStatus.RETRY_WAIT,
                fixture.tasks.findById(fixture.request.getTaskId()).orElseThrow().getStatus());
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void exhaustedAttemptBudgetEmitsExactlyOneDeadLetterEvent() {
        Fixture fixture = fixture(1);

        fixture.service.execute(fixture.request.getDispatchRequestId());

        assertEquals(DispatchRequestStatus.DEAD_LETTER,
                fixture.repository.findById(fixture.request.getDispatchRequestId()).orElseThrow().getStatus());
        TaskRecord deadLetterTask = fixture.tasks.findById(fixture.request.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.DEAD_LETTER, deadLetterTask.getStatus());
        assertEquals(null, deadLetterTask.getNextDispatchAttemptAt());
        assertEquals(1, fixture.events.size());
        assertTrue(fixture.events.get(0) instanceof DispatchDeadLetteredEvent);
        assertEquals("dispatch-dead-letter-dispatch-1-1", fixture.events.get(0).eventId());
    }

    private Fixture fixture(int maxAttempts) {
        InMemoryDispatchRequestRepository dispatches = new InMemoryDispatchRequestRepository();
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-1");
        task.setStatus(TaskStatus.ASSIGNED);
        task.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        task.setUpdatedAt(task.getCreatedAt());
        tasks.save(task);

        DispatchRequest request = new DispatchRequest();
        request.setDispatchRequestId("dispatch-1");
        request.setTaskId(task.getTaskId());
        request.setAssignmentId("assignment-1");
        request.setIncidentId("incident-1");
        request.setAgentId("agent-1");
        request.setStatus(DispatchRequestStatus.APPROVED);
        request.setCreatedAt(task.getCreatedAt());
        request.setUpdatedAt(task.getCreatedAt());
        dispatches.save(request);

        DispatchProperties properties = new DispatchProperties();
        properties.getRetry().setEnabled(true);
        properties.getRetry().setMaxAttempts(maxAttempts);

        List<ModuleEvent> events = new ArrayList<>();
        ModuleEventPublisher publisher = event -> {
            events.add(event);
            OutboxEventRecord record = new OutboxEventRecord();
            record.setEventId(event.eventId());
            record.setEventType(event.eventType());
            return record;
        };

        DispatchExecutionService service = new DispatchExecutionService(
                dispatches,
                new DefaultTaskOrchestrationFacade(null, null, tasks),
                ignored -> GatewayDispatchResult.failure(503, "UNAVAILABLE", "gateway unavailable"),
                properties,
                ExecutionMetricsPort.noop(),
                publisher);
        return new Fixture(service, dispatches, tasks, request, events);
    }

    private record Fixture(
            DispatchExecutionService service,
            InMemoryDispatchRequestRepository repository,
            InMemoryTaskRepository tasks,
            DispatchRequest request,
            List<ModuleEvent> events) {
    }
}
