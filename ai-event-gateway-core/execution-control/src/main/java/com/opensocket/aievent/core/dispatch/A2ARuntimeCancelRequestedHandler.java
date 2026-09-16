package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.a2a.A2ACancellationRepository;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.events.A2ARuntimeCancelDeliveryEvent;
import com.opensocket.aievent.core.events.A2ARuntimeCancelRequestedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;

@Component
public class A2ARuntimeCancelRequestedHandler
        implements ModuleEventHandler<A2ARuntimeCancelRequestedEvent> {
    private final A2ACancellationRepository cancellations;
    private final DispatchRequestRepository dispatches;
    private final TaskAssignmentRepository assignments;
    private final GatewayCancellationClient client;
    private final ModuleEventPublisher events;

    public A2ARuntimeCancelRequestedHandler(A2ACancellationRepository cancellations,
            DispatchRequestRepository dispatches, TaskAssignmentRepository assignments,
            GatewayCancellationClient client, ModuleEventPublisher events) {
        this.cancellations = cancellations;
        this.dispatches = dispatches;
        this.assignments = assignments;
        this.client = client;
        this.events = events;
    }

    @Override public String eventType() { return A2ARuntimeCancelRequestedEvent.TYPE; }
    @Override public Class<A2ARuntimeCancelRequestedEvent> payloadType() {
        return A2ARuntimeCancelRequestedEvent.class;
    }

    @Override
    public void handle(A2ARuntimeCancelRequestedEvent event) {
        A2ACancellationRecord cancellation = cancellations
                .findById(event.tenantId(), event.cancellationId())
                .orElseThrow(() -> new IllegalStateException(
                        "A2A cancellation not found: " + event.cancellationId()));
        DispatchRequest dispatch = dispatches.findById(event.dispatchRequestId()).orElse(null);
        TaskAssignment assignment = event.assignmentId() == null
                ? null : assignments.findById(event.assignmentId()).orElse(null);
        GatewayCancellationResult result = client.cancel(cancellation, dispatch, assignment);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        events.publish(new A2ARuntimeCancelDeliveryEvent(
                "evt-" + UUID.randomUUID(), event.tenantId(), event.cancellationId(),
                event.a2aRequestId(), event.childTaskId(), result.accepted(),
                result.httpStatus(), result.status(), result.errorCode(), result.message(), now));
    }
}
