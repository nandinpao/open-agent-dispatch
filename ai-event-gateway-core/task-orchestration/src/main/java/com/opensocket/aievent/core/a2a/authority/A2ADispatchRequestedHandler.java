package com.opensocket.aievent.core.a2a.authority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.a2a.A2ABlockerCode;
import com.opensocket.aievent.core.a2a.A2ADispatchProgressCommand;
import com.opensocket.aievent.core.a2a.application.port.in.A2AGovernanceUseCase;
import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.events.A2ADispatchRequestedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;

/**
 * Consumes the A2A dispatch intent after the creating transaction commits.
 * Assignment and Dispatch Request creation remain owned by the normal Task and
 * Dispatch authorities; retries are safe because both paths reject duplicates.
 */
@Component
public class A2ADispatchRequestedHandler
        implements ModuleEventHandler<A2ADispatchRequestedEvent> {
    private static final Logger log = LoggerFactory.getLogger(A2ADispatchRequestedHandler.class);

    private final TaskRepository tasks;
    private final TaskAssignmentService assignments;
    private final A2AGovernanceUseCase governance;

    public A2ADispatchRequestedHandler(
            TaskRepository tasks,
            TaskAssignmentService assignments,
            A2AGovernanceUseCase governance) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.governance = governance;
    }

    @Override
    public String eventType() {
        return A2ADispatchRequestedEvent.TYPE;
    }

    @Override
    public Class<A2ADispatchRequestedEvent> payloadType() {
        return A2ADispatchRequestedEvent.class;
    }

    @Override
    @Transactional
    public void handle(A2ADispatchRequestedEvent event) {
        TaskRecord task = tasks.findByTenantAndId(event.tenantId(), event.childTaskId())
                .orElseThrow(() -> new IllegalStateException(
                        "A2A Child Task not found for dispatch event: " + event.childTaskId()));
        if (task.getStatus() == null || !task.getStatus().isDispatchReady()) {
            throw new IllegalStateException(
                    "A2A Child Task is not dispatch-ready: taskId=" + task.getTaskId()
                            + " status=" + task.getStatus());
        }
        String taskPool = firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId());
        if (!event.targetAgentPoolId().equals(taskPool)) {
            throw new IllegalStateException(
                    "A2A dispatch target pool no longer matches Child Task: expected="
                            + event.targetAgentPoolId() + " actual=" + taskPool);
        }
        AssignmentDecisionResult result = assignments.assignIfPossible(task);
        log.info("a2a_dispatch_assignment_result tenantId={} a2aRequestId={} childTaskId={} targetPoolId={} assignmentCreated={} assignmentId={} selectedAgentId={} routingDecisionId={} assignmentStatus={} dispatchRequestCreated={} dispatchRequestId={} dispatchStatus={} dispatchEligibilityStatus={} reason={} dispatchReason={}",
                event.tenantId(), event.a2aRequestId(), event.childTaskId(), event.targetAgentPoolId(),
                result.assignmentCreated(), result.assignmentId(), result.selectedAgentId(), result.routingDecisionId(),
                result.assignmentStatus(), result.dispatchRequestCreated(), result.dispatchRequestId(),
                result.dispatchStatus(), result.dispatchEligibilityStatus(), result.reason(), result.dispatchReason());

        // The ModuleEvent is a one-shot dispatch intent. A non-throwing routing/assignment
        // failure must therefore be projected back into A2A state; otherwise the outbox
        // event is acknowledged while the A2A Request remains stuck in DISPATCH_REQUESTED.
        if (blank(result.assignmentId())) {
            recordBlocked(event, result, blockerForAssignment(result));
            return;
        }
        if (blank(result.dispatchRequestId())) {
            recordBlocked(event, result, blockerForDispatch(result));
        }
    }

    private void recordBlocked(A2ADispatchRequestedEvent event, AssignmentDecisionResult result, A2ABlockerCode blockerCode) {
        String evidence = "a2a-assignment:" + event.eventId() + ":" + firstNonBlank(result.routingDecisionId(), "no-routing-decision");
        String reason = firstNonBlank(result.dispatchReason(), result.reason());
        governance.recordDispatchProgress(new A2ADispatchProgressCommand(
                event.tenantId(),
                event.childTaskId(),
                result.dispatchRequestId(),
                "FAILED_RETRYABLE",
                blockerCode,
                firstNonBlank(reason, "A2A Child Task could not enter canonical Dispatch."),
                evidence,
                "DISPATCH_AUTHORITY",
                event.occurredAt()));
        log.warn("a2a_dispatch_blocked tenantId={} a2aRequestId={} childTaskId={} targetPoolId={} blockerCode={} routingDecisionId={} assignmentId={} dispatchRequestId={} reason={}",
                event.tenantId(), event.a2aRequestId(), event.childTaskId(), event.targetAgentPoolId(), blockerCode,
                result.routingDecisionId(), result.assignmentId(), result.dispatchRequestId(), reason);
    }

    private A2ABlockerCode blockerForAssignment(AssignmentDecisionResult result) {
        String status = upper(result.assignmentStatus());
        String reason = upper(result.reason());
        if (status.contains("MANUAL_REVIEW") || reason.contains("MANUAL_REVIEW")) {
            return A2ABlockerCode.MANUAL_REVIEW_REQUIRED;
        }
        if (reason.contains("OFFLINE") || reason.contains("UNAVAILABLE") || reason.contains("CAPACITY")) {
            return A2ABlockerCode.AGENT_UNAVAILABLE;
        }
        return A2ABlockerCode.NO_ELIGIBLE_AGENT;
    }

    private A2ABlockerCode blockerForDispatch(AssignmentDecisionResult result) {
        String eligibility = upper(result.dispatchEligibilityStatus());
        String status = upper(result.dispatchStatus());
        if (eligibility.contains("NOT_ELIGIBLE") || status.contains("SUPPRESSED")) {
            return A2ABlockerCode.DISPATCH_FAILED;
        }
        return A2ABlockerCode.DISPATCH_REQUEST_PENDING;
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
