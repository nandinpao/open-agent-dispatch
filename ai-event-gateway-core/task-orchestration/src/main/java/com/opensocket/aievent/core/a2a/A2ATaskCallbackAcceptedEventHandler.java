package com.opensocket.aievent.core.a2a;

import com.opensocket.aievent.core.a2a.application.port.in.A2AResultAcceptanceUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2AGovernanceUseCase;
import com.opensocket.aievent.core.a2a.core.A2AResultFingerprint;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;

/** Converts an already accepted terminal callback into an evidence-bearing A2A result submission. */
@Component
public class A2ATaskCallbackAcceptedEventHandler implements ModuleEventHandler<TaskCallbackAcceptedEvent> {
    private static final Logger log = LoggerFactory.getLogger(A2ATaskCallbackAcceptedEventHandler.class);
    private final A2ARequestRepository requests;
    private final A2AResultAcceptanceUseCase acceptance;
    private final A2ACancellationRepository cancellations;
    private final A2AGovernanceUseCase governance;

    @Autowired
    public A2ATaskCallbackAcceptedEventHandler(A2ARequestRepository requests,
            A2AResultAcceptanceUseCase acceptance, A2ACancellationRepository cancellations,
            A2AGovernanceUseCase governance) {
        this.requests = requests; this.acceptance = acceptance; this.cancellations = cancellations; this.governance = governance;
    }

    /** Compatibility constructor used by focused unit tests. */
    public A2ATaskCallbackAcceptedEventHandler(A2ARequestRepository requests,
            A2AResultAcceptanceUseCase acceptance, A2ACancellationRepository cancellations) {
        this(requests, acceptance, cancellations, null);
    }

    @Override public String eventType() { return TaskCallbackAcceptedEvent.TYPE; }
    @Override public Class<TaskCallbackAcceptedEvent> payloadType() { return TaskCallbackAcceptedEvent.class; }

    @Override
    public void handle(TaskCallbackAcceptedEvent event) {
        if (event == null) return;
        if (governance != null && ("ACK".equalsIgnoreCase(event.callbackType())
                || "PROGRESS".equalsIgnoreCase(event.callbackType()))) {
            governance.recordDispatchProgress(new A2ADispatchProgressCommand(event.tenantId(), event.taskId(),
                    event.dispatchRequestId(), "ACK", A2ABlockerCode.NONE, event.message(),
                    "callback:" + event.callbackId(), "RUNTIME_AUTHORITY", event.occurredAt()));
            if ("PROGRESS".equalsIgnoreCase(event.callbackType())
                    && event.progressPercent() != null && event.progressPercent() >= 100) {
                governance.recordDispatchProgress(new A2ADispatchProgressCommand(event.tenantId(), event.taskId(),
                        event.dispatchRequestId(), "WAITING_RESULT", A2ABlockerCode.NONE, event.message(),
                        "callback-waiting-result:" + event.callbackId(), "RUNTIME_AUTHORITY", event.occurredAt()));
            }
            return;
        }
        if (!("RESULT".equalsIgnoreCase(event.callbackType()) || "ERROR".equalsIgnoreCase(event.callbackType()))) return;
        if (blank(event.tenantId()) || blank(event.taskId())) return;
        A2ARequest request = requests.findByChildTask(event.tenantId(), event.taskId()).orElse(null);
        if (request == null) return;
        A2AResultStatus status = status(event);
        if (status == A2AResultStatus.CANCELLED
                && cancellations.findByRequest(event.tenantId(), request.getRequestId()).isPresent()) {
            log.info("a2a_cancel_callback_deferred_to_cancellation_authority requestId={} childTaskId={} callbackId={}",
                    request.getRequestId(), event.taskId(), event.callbackId());
            return;
        }
        A2AResultSubmission unsigned = new A2AResultSubmission(
                event.tenantId(), request.getRequestId(), status,
                first(event.message(), event.errorMessage(), event.resultStatus()), null,
                blank(event.callbackFingerprint()) ? List.of() : List.of(event.callbackFingerprint()),
                event.agentId(), event.assignmentId(), null, event.attemptNo(), event.dispatchRequestId(),
                event.dispatchTokenHash(), event.fencingTokenHash(), event.agentSessionId(), event.callbackId(),
                event.payloadHash(), 1, "pending", "a2a-result-callback:" + event.callbackId(),
                request.getCorrelationId(), event.occurredAt());
        A2AResultSubmission submission = new A2AResultSubmission(
                unsigned.tenantId(), unsigned.requestId(), unsigned.resultStatus(), unsigned.resultSummary(),
                unsigned.resultPayloadRef(), unsigned.evidenceRefs(), unsigned.completedByAgentId(),
                unsigned.assignmentId(), unsigned.executionAttemptId(), unsigned.attemptNo(),
                unsigned.dispatchRequestId(), unsigned.dispatchTokenHash(), unsigned.fencingTokenHash(),
                unsigned.agentSessionId(), unsigned.callbackInboxId(), unsigned.payloadHash(),
                unsigned.resultSchemaVersion(), A2AResultFingerprint.evidence(unsigned), unsigned.idempotencyKey(),
                unsigned.correlationId(), unsigned.occurredAt());
        try {
            A2AResult result = acceptance.accept(submission);
            log.info("a2a_canonical_result_accepted requestId={} childTaskId={} resultId={} callbackId={} status={}",
                    request.getRequestId(), event.taskId(), result.getResultId(), event.callbackId(), status);
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("A2A_RESULT_QUARANTINED:")) {
                log.warn("a2a_result_quarantined requestId={} childTaskId={} callbackId={} reason={}",
                        request.getRequestId(), event.taskId(), event.callbackId(), ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private A2AResultStatus status(TaskCallbackAcceptedEvent event) {
        String value = event.resultStatus();
        if ("CANCELLED".equalsIgnoreCase(value)
                || "TASK_CANCELLED".equalsIgnoreCase(event.errorCode())) {
            return A2AResultStatus.CANCELLED;
        }
        if ("ERROR".equalsIgnoreCase(event.callbackType())) return A2AResultStatus.FAILED;
        if ("FAILED".equalsIgnoreCase(value) || "ERROR".equalsIgnoreCase(value)) return A2AResultStatus.FAILED;
        if ("PARTIAL".equalsIgnoreCase(value) || "PARTIALLY_COMPLETED".equalsIgnoreCase(value)) return A2AResultStatus.PARTIAL;
        return A2AResultStatus.SUCCEEDED;
    }
    private boolean blank(String value){return value==null||value.isBlank();}
    private String first(String... values){for(String value:values)if(!blank(value))return value;return null;}
}
