package com.opensocket.aievent.core.a2a;

import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationRuntimeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;

/** Converts an accepted task.cancelled callback into cancellation acknowledgement evidence. */
@Component
public class A2ACancellationCallbackAcceptedEventHandler
        implements ModuleEventHandler<TaskCallbackAcceptedEvent> {
    private static final Logger log = LoggerFactory.getLogger(
            A2ACancellationCallbackAcceptedEventHandler.class);
    private final A2ARequestRepository requests;
    private final A2ACancellationRepository cancellations;
    private final A2ACancellationRuntimeUseCase service;

    public A2ACancellationCallbackAcceptedEventHandler(A2ARequestRepository requests,
            A2ACancellationRepository cancellations, A2ACancellationRuntimeUseCase service) {
        this.requests = requests;
        this.cancellations = cancellations;
        this.service = service;
    }

    @Override public String eventType() { return TaskCallbackAcceptedEvent.TYPE; }
    @Override public Class<TaskCallbackAcceptedEvent> payloadType() {
        return TaskCallbackAcceptedEvent.class;
    }

    @Override
    public void handle(TaskCallbackAcceptedEvent event) {
        if (event == null || !isCancellation(event) || blank(event.tenantId())
                || blank(event.taskId())) return;
        A2ARequest request = requests.findByChildTask(event.tenantId(), event.taskId()).orElse(null);
        if (request == null) return;
        A2ACancellationRecord cancellation = cancellations
                .findByRequest(event.tenantId(), request.getRequestId()).orElse(null);
        if (cancellation == null) return;
        if (cancellation.getStatus() == A2ACancellationStatus.ACKNOWLEDGED) return;

        service.acknowledge(new A2ACancellationAckCommand(
                event.tenantId(), cancellation.getCancellationId(), "ACKNOWLEDGED",
                event.callbackId(), event.assignmentId(), cancellation.getExecutionAttemptId(),
                event.attemptNo(), event.agentSessionId(), event.fencingTokenHash(),
                first(event.message(), event.errorMessage(), "Agent reported task.cancelled"),
                event.occurredAt()));
        log.info("a2a_cancellation_callback_acknowledged tenantId={} requestId={} cancellationId={} callbackId={}",
                event.tenantId(), request.getRequestId(), cancellation.getCancellationId(),
                event.callbackId());
    }

    private boolean isCancellation(TaskCallbackAcceptedEvent event) {
        return "CANCELLED".equalsIgnoreCase(event.resultStatus())
                || "TASK_CANCELLED".equalsIgnoreCase(event.errorCode());
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String first(String... values) {
        for (String value : values) if (!blank(value)) return value;
        return null;
    }
}
