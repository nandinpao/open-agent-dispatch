package com.opensocket.aievent.core.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.assignment.AssignmentFencingTokenPolicy;
import com.opensocket.aievent.core.assignment.AssignmentFencingValidation;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchStatusTransition;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttemptService;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.core.task.TaskExecutionStateTransition;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.dispatch.ExecutionMetricsPort;
import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.events.TaskCallbackAcceptedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;

@Service
public class TaskCallbackService {
    private static final Logger log = LoggerFactory.getLogger(TaskCallbackService.class);

    private final TaskCallbackRepository callbackRepository;
    private final DispatchRequestRepository dispatchRepository;
    private final TaskCallbackProperties properties;
    private final TaskTerminalActionPort terminalActionPort;
    private final ModuleEventPublisher eventPublisher;
    private final boolean eventDrivenTerminalFlow;
    private final TaskOrchestrationFacade taskOrchestrationFacade;

    private final ExecutionMetricsPort metrics;

    @Autowired(required = false)
    private TaskAssignmentRepository assignmentRepository;

    @Autowired(required = false)
    private AssignmentFencingTokenPolicy assignmentFencingTokenPolicy = new AssignmentFencingTokenPolicy();

    @Autowired(required = false)
    private TaskExecutionAttemptService executionAttemptService;

    @Autowired(required = false)
    private List<TaskCallbackAcceptanceGuard> callbackAcceptanceGuards = List.of();

    /** Compatibility constructor used by focused unit tests. */
    public TaskCallbackService(TaskCallbackRepository callbackRepository,
                               DispatchRequestRepository dispatchRepository,
                               TaskOrchestrationFacade taskOrchestrationFacade,
                               TaskCallbackProperties properties,
                               TaskTerminalActionPort terminalActionPort) {
        this(callbackRepository, dispatchRepository, taskOrchestrationFacade, properties,
                terminalActionPort, ModuleEventPublisher.noop(), false, ExecutionMetricsPort.noop());
    }

    @Autowired
    public TaskCallbackService(TaskCallbackRepository callbackRepository,
                               DispatchRequestRepository dispatchRepository,
                               TaskOrchestrationFacade taskOrchestrationFacade,
                               TaskCallbackProperties properties,
                               ObjectProvider<ModuleEventPublisher> eventPublisherProvider,
                               ObjectProvider<ExecutionMetricsPort> metricsProvider) {
        this(callbackRepository, dispatchRepository, taskOrchestrationFacade, properties,
                TaskTerminalActionPort.noop(),
                eventPublisherProvider.getIfAvailable(ModuleEventPublisher::noop),
                true,
                metricsProvider.getIfAvailable(ExecutionMetricsPort::noop));
    }

    private TaskCallbackService(TaskCallbackRepository callbackRepository,
                                DispatchRequestRepository dispatchRepository,
                                TaskOrchestrationFacade taskOrchestrationFacade,
                                TaskCallbackProperties properties,
                                TaskTerminalActionPort terminalActionPort,
                                ModuleEventPublisher eventPublisher,
                                boolean eventDrivenTerminalFlow,
                                ExecutionMetricsPort metrics) {
        this.callbackRepository = callbackRepository;
        this.dispatchRepository = dispatchRepository;
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.properties = properties;
        this.terminalActionPort = terminalActionPort == null ? TaskTerminalActionPort.noop() : terminalActionPort;
        this.eventPublisher = eventPublisher == null ? ModuleEventPublisher.noop() : eventPublisher;
        this.eventDrivenTerminalFlow = eventDrivenTerminalFlow;
        this.metrics = metrics == null ? ExecutionMetricsPort.noop() : metrics;
    }

    public TaskCallbackResult ack(String taskId, TaskCallbackRequest request) {
        return handle(TaskCallbackType.ACK, taskId, request);
    }

    public TaskCallbackResult progress(String taskId, TaskCallbackRequest request) {
        return handle(TaskCallbackType.PROGRESS, taskId, request);
    }

    public TaskCallbackResult result(String taskId, TaskCallbackRequest request) {
        return handle(TaskCallbackType.RESULT, taskId, request);
    }

    public TaskCallbackResult error(String taskId, TaskCallbackRequest request) {
        return handle(TaskCallbackType.ERROR, taskId, request);
    }

    @Transactional
    public TaskCallbackResult handle(TaskCallbackType type, String pathTaskId, TaskCallbackRequest rawRequest) {
        TaskCallbackRequest request = rawRequest == null ? new TaskCallbackRequest() : rawRequest;
        String taskId = firstNonBlank(pathTaskId, request.getTaskId());
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        request.setTaskId(taskId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (request.getOccurredAt() == null) {
            request.setOccurredAt(now);
        }
        String callbackId = firstNonBlank(request.getCallbackId(), generatedCallbackId(type, request));
        request.setCallbackId(callbackId);

        log.info("callback_inbox_processing_started taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={} attemptNo={} idempotencyKey={}",
                taskId, type, callbackId, request.getDispatchRequestId(), request.getAssignmentId(), request.getAgentId(),
                request.getAttemptNo(), idempotencyKey(type, request));

        TaskCallbackRecord record = recordFrom(type, request, now);
        if (properties.isIdempotencyEnabled() && !callbackRepository.tryReserve(record)) {
            TaskCallbackRecord previous = callbackRepository.findByCallbackId(callbackId).orElse(record);
            if (isReplayMismatch(record, previous)) {
                if (previous.isAccepted() && isTerminalCallback(type)) {
                    log.info("callback_replay_duplicate_accepted taskId={} callbackType={} callbackId={} dispatchRequestId={} previousTaskStatus={} previousDispatchStatus={} reason=PREVIOUS_TERMINAL_CALLBACK_ALREADY_ACCEPTED",
                            taskId, type, callbackId, request.getDispatchRequestId(), previous.getNewTaskStatus(), previous.getNewDispatchStatus());
                    return record(resultFrom(previous, true, true, null,
                            "Duplicate terminal callback already accepted; replay fingerprint drift ignored"));
                }
                record.setDuplicate(true);
                record.setReplayDetected(true);
                record.setAccepted(false);
                record.setIgnoredReason("CALLBACK_REPLAY_MISMATCH");
                record.setPreviousTaskStatus(previous.getPreviousTaskStatus());
                record.setNewTaskStatus(previous.getNewTaskStatus());
                record.setPreviousDispatchStatus(previous.getPreviousDispatchStatus());
                record.setNewDispatchStatus(previous.getNewDispatchStatus());
                log.warn("callback_inbox_rejected taskId={} callbackType={} callbackId={} dispatchRequestId={} reason={}",
                        taskId, type, callbackId, request.getDispatchRequestId(), "CALLBACK_REPLAY_MISMATCH");
                return record(resultFrom(record, true, false, "CALLBACK_REPLAY_MISMATCH",
                        "Callback id replay rejected because payload fingerprint differs from the reserved callback"));
            }
            log.info("callback_inbox_duplicate taskId={} callbackType={} callbackId={} dispatchRequestId={} accepted={} ignoredReason={}",
                    taskId, type, callbackId, request.getDispatchRequestId(), previous.isAccepted(), previous.getIgnoredReason());
            return record(resultFrom(previous, true, previous.isAccepted(), previous.getIgnoredReason(), "Duplicate callback ignored"));
        }

        DispatchRequest dispatchRequest = resolveDispatchRequest(taskId, request);
        log.info("callback_inbox_dispatch_resolved taskId={} callbackType={} callbackId={} requestedDispatchRequestId={} resolvedDispatchRequestId={} resolvedDispatchStatus={}",
                taskId, type, callbackId, request.getDispatchRequestId(),
                dispatchRequest == null ? null : dispatchRequest.getDispatchRequestId(),
                dispatchRequest == null ? null : dispatchRequest.getStatus());
        if (dispatchRequest == null && !properties.isAllowMissingDispatchRequestId()) {
            return ignore(record, null, null, "MISSING_DISPATCH_REQUEST", "dispatchRequestId is required and no active dispatch could be resolved");
        }

        TaskRecord task = taskOrchestrationFacade.findTask(taskId).orElse(null);
        if (task == null) {
            log.warn("callback_inbox_rejected taskId={} callbackType={} callbackId={} dispatchRequestId={} reason={}",
                    taskId, type, callbackId, request.getDispatchRequestId(), "TASK_NOT_FOUND");
            return ignore(record, dispatchRequest, null, "TASK_NOT_FOUND", "Task not found: " + taskId);
        }

        String previousTaskStatus = name(task.getStatus());
        String previousDispatchStatus = dispatchRequest == null ? null : name(dispatchRequest.getStatus());
        record.setPreviousTaskStatus(previousTaskStatus);
        record.setPreviousDispatchStatus(previousDispatchStatus);
        if (dispatchRequest != null) {
            request.setAssignmentId(firstNonBlank(request.getAssignmentId(), dispatchRequest.getAssignmentId()));
            request.setAgentId(firstNonBlank(request.getAgentId(), dispatchRequest.getAgentId()));
            request.setOwnerGatewayNodeId(firstNonBlank(request.getOwnerGatewayNodeId(), dispatchRequest.getOwnerGatewayNodeId()));
            request.setAgentSessionId(firstNonBlank(request.getAgentSessionId(), dispatchRequest.getAgentSessionId()));
            record.setDispatchRequestId(dispatchRequest.getDispatchRequestId());
            record.setAssignmentId(request.getAssignmentId());
            record.setAgentId(request.getAgentId());
            record.setOwnerGatewayNodeId(request.getOwnerGatewayNodeId());
            record.setAgentSessionId(request.getAgentSessionId());
        }

        String rejection = validateCallback(type, request, dispatchRequest, task);
        if (rejection == null) {
            rejection = validateAcceptanceGuards(type, request, dispatchRequest, task);
        }
        if (rejection != null) {
            log.warn("callback_inbox_rejected taskId={} callbackType={} callbackId={} dispatchRequestId={} dispatchStatus={} taskStatus={} reason={}",
                    taskId, type, callbackId, request.getDispatchRequestId(),
                    dispatchRequest == null ? null : dispatchRequest.getStatus(), task.getStatus(), rejection);
            markStaleExecutionAttempt(request, rejection);
            return ignore(record, dispatchRequest, task, rejection, "Callback rejected: " + rejection);
        }

        if (dispatchRequest != null) {
            PersistenceWriteResult dispatchTransition = transitionDispatch(type, dispatchRequest, request, now);
            if (!dispatchTransition.applied()) {
                DispatchRequest latest = dispatchRepository.findById(dispatchRequest.getDispatchRequestId()).orElse(dispatchRequest);
                String reason = resolveConcurrentDispatchReason(latest);
                log.warn("callback_inbox_rejected taskId={} callbackType={} callbackId={} dispatchRequestId={} dispatchStatus={} reason={}",
                        taskId, type, callbackId, dispatchRequest.getDispatchRequestId(), latest == null ? null : latest.getStatus(), reason);
                return ignore(record, latest, task, reason,
                        "Callback rejected by atomic dispatch transition guard");
            }
            dispatchRequest = dispatchRepository.findById(dispatchRequest.getDispatchRequestId()).orElse(dispatchRequest);

            boolean taskTransitioned = taskOrchestrationFacade.transitionExecutionState(taskTransition(type, task, request, now));
            task = taskOrchestrationFacade.findTask(taskId).orElse(task);
            log.info("callback_task_transition_applied taskId={} callbackType={} callbackId={} dispatchRequestId={} taskTransitioned={} taskStatus={} dispatchStatus={}",
                    taskId, type, callbackId, dispatchRequest.getDispatchRequestId(), taskTransitioned, task.getStatus(), dispatchRequest.getStatus());
            if (!taskTransitioned && isTerminal(task.getStatus())) {
                record.setMessage(firstNonBlank(record.getMessage(), "Task was already terminal; dispatch transition was accepted and terminal task state was preserved"));
            }

            updateExecutionAttemptFromCallback(type, request);

            if (type == TaskCallbackType.RESULT || type == TaskCallbackType.ERROR) {
                releaseAssignmentReservation(dispatchRequest);
            }
        } else {
            applyTaskTransition(type, task, request, now);
            taskOrchestrationFacade.saveExecutionState(task);
            updateExecutionAttemptFromCallback(type, request);
        }

        if (type == TaskCallbackType.RESULT || type == TaskCallbackType.ERROR) {
            if (eventDrivenTerminalFlow) {
                log.info("callback_terminal_event_published taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={} taskStatus={}",
                        taskId, type, callbackId, dispatchRequest == null ? request.getDispatchRequestId() : dispatchRequest.getDispatchRequestId(),
                        dispatchRequest == null ? request.getAssignmentId() : dispatchRequest.getAssignmentId(),
                        dispatchRequest == null ? request.getAgentId() : dispatchRequest.getAgentId(), task.getStatus());
                eventPublisher.publish(toTaskTerminalEvent(task, dispatchRequest, request, type, now));
            } else {
                terminalActionPort.onTerminalTaskCallback(task, dispatchRequest, request, type);
            }
        }

        record.setAccepted(true);
        record.setIgnoredReason(null);
        record.setNewTaskStatus(name(task.getStatus()));
        record.setNewDispatchStatus(dispatchRequest == null ? null : name(dispatchRequest.getStatus()));
        callbackRepository.save(record);
        eventPublisher.publish(toTaskCallbackAcceptedEvent(record, request, task, dispatchRequest, now));
        log.info("callback_accepted_event_published taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={}",
                taskId, type, callbackId, record.getDispatchRequestId(), record.getAssignmentId(), record.getAgentId());
        log.info("callback_inbox_accepted taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={} previousTaskStatus={} newTaskStatus={} previousDispatchStatus={} newDispatchStatus={}",
                taskId, type, callbackId, record.getDispatchRequestId(), record.getAssignmentId(), record.getAgentId(),
                record.getPreviousTaskStatus(), record.getNewTaskStatus(), record.getPreviousDispatchStatus(), record.getNewDispatchStatus());
        return record(resultFrom(record, false, true, null, "Callback processed"));
    }

    private TaskCallbackAcceptedEvent toTaskCallbackAcceptedEvent(
            TaskCallbackRecord record, TaskCallbackRequest request, TaskRecord task, DispatchRequest dispatch, OffsetDateTime now) {
        return new TaskCallbackAcceptedEvent(
                "evt-" + UUID.randomUUID(),
                record.getCallbackId(),
                record.getCallbackType() == null ? null : record.getCallbackType().name(),
                record.getTaskId(),
                task == null ? null : task.getTenantId(),
                dispatch == null ? record.getDispatchRequestId() : dispatch.getDispatchRequestId(),
                dispatch == null ? record.getAssignmentId() : dispatch.getAssignmentId(),
                dispatch == null ? record.getAgentId() : dispatch.getAgentId(),
                record.getNewTaskStatus(),
                record.getNewDispatchStatus(),
                record.getIdempotencyKey(),
                record.getCallbackFingerprint(),
                request.getResultStatus(),
                request.getErrorCode(),
                request.getErrorMessage(),
                request.getMessage(),
                request.getProgressPercent(),
                request.getPayload(),
                now,
                request.getOccurredAt() == null ? now : request.getOccurredAt());
    }

    private TaskTerminalEvent toTaskTerminalEvent(TaskRecord task,
                                                  DispatchRequest dispatch,
                                                  TaskCallbackRequest callback,
                                                  TaskCallbackType callbackType,
                                                  OffsetDateTime now) {
        return new TaskTerminalEvent(
                "evt-" + UUID.randomUUID(),
                task.getTaskId(),
                task.getIncidentId(),
                task.getSourceEventId(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getTaskType() == null ? null : task.getTaskType().name(),
                task.getPriority() == null ? null : task.getPriority().name(),
                task.getTenantId(),
                task.getSiteId(),
                task.getPlantId(),
                task.getObjectType(),
                task.getObjectId(),
                task.getEventType(),
                task.getErrorCode(),
                task.getRoutingPolicy(),
                task.getRequiredCapabilities(),
                dispatch == null ? callback.getDispatchRequestId() : dispatch.getDispatchRequestId(),
                dispatch == null ? callback.getAssignmentId() : dispatch.getAssignmentId(),
                dispatch == null ? callback.getAgentId() : dispatch.getAgentId(),
                dispatch == null ? callback.getOwnerGatewayNodeId() : dispatch.getOwnerGatewayNodeId(),
                dispatch == null ? callback.getAgentSessionId() : dispatch.getAgentSessionId(),
                callback.getCallbackId(),
                callbackType.name(),
                callback.getMessage(),
                callback.getResultStatus(),
                callback.getErrorCode(),
                callback.getErrorMessage(),
                callback.getPayload(),
                callback.getOccurredAt() == null ? now : callback.getOccurredAt());
    }

    private void releaseAssignmentReservation(DispatchRequest dispatchRequest) {
        if (taskOrchestrationFacade != null
                && dispatchRequest.getAssignmentId() != null
                && !dispatchRequest.getAssignmentId().isBlank()) {
            taskOrchestrationFacade.releaseCapacityReservation(dispatchRequest.getAssignmentId());
        }
    }

    public List<TaskCallbackRecord> recent(int limit) {
        return callbackRepository.recent(Math.max(1, Math.min(limit, properties.getMaxRecent())));
    }

    public List<TaskCallbackRecord> byTask(String taskId, int limit) {
        return callbackRepository.findByTaskId(taskId, Math.max(1, Math.min(limit, properties.getMaxRecent())));
    }

    private DispatchRequest resolveDispatchRequest(String taskId, TaskCallbackRequest request) {
        if (request.getDispatchRequestId() != null && !request.getDispatchRequestId().isBlank()) {
            return dispatchRepository.findById(request.getDispatchRequestId()).orElse(null);
        }
        return dispatchRepository.findByTaskId(taskId, 20).stream()
                .filter(r -> r.getStatus() == DispatchRequestStatus.DISPATCHED
                        || r.getStatus() == DispatchRequestStatus.ACKED
                        || r.getStatus() == DispatchRequestStatus.RUNNING)
                .findFirst()
                .orElse(null);
    }

    private String validateAcceptanceGuards(
            TaskCallbackType type, TaskCallbackRequest request, DispatchRequest dispatch, TaskRecord task) {
        if (callbackAcceptanceGuards == null || callbackAcceptanceGuards.isEmpty()) {
            return null;
        }
        TaskCallbackGuardContext context = new TaskCallbackGuardContext(
                task.getTenantId(),
                task.getTaskId(),
                request.getCallbackId(),
                type,
                dispatch == null ? request.getDispatchRequestId() : dispatch.getDispatchRequestId(),
                dispatch == null ? request.getAssignmentId() : dispatch.getAssignmentId(),
                dispatch == null ? request.getAgentId() : dispatch.getAgentId(),
                idempotencyKey(type, request),
                request.getPayload(),
                request.getOccurredAt());
        return callbackAcceptanceGuards.stream()
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(TaskCallbackAcceptanceGuard::order))
                .map(guard -> evaluateGuard(guard, context))
                .filter(decision -> !decision.allowed())
                .map(TaskCallbackGuardDecision::reasonCode)
                .findFirst()
                .orElse(null);
    }

    private TaskCallbackGuardDecision evaluateGuard(
            TaskCallbackAcceptanceGuard guard, TaskCallbackGuardContext context) {
        try {
            TaskCallbackGuardDecision decision = guard.evaluate(context);
            return decision == null
                    ? TaskCallbackGuardDecision.block(
                            "CALLBACK_ACCEPTANCE_GUARD_EMPTY_DECISION",
                            "Callback acceptance guard returned no decision")
                    : decision;
        } catch (RuntimeException ex) {
            log.error(
                    "callback_acceptance_guard_failed taskId={} callbackId={} guard={} authoritativeStateUnchanged=true",
                    context.taskId(), context.callbackId(), guard.getClass().getName(), ex);
            return TaskCallbackGuardDecision.block(
                    "CALLBACK_ACCEPTANCE_GUARD_FAILED",
                    "Callback acceptance guard failed closed");
        }
    }

    private String validateCallback(TaskCallbackType type, TaskCallbackRequest request, DispatchRequest dispatch, TaskRecord task) {
        if (dispatch == null) {
            return null;
        }
        if (properties.isRequireDispatchToken()) {
            String expected = dispatch.getDispatchToken();
            if (expected == null || expected.isBlank()) {
                return "DISPATCH_TOKEN_NOT_ISSUED";
            }
            if (request.getDispatchToken() == null || request.getDispatchToken().isBlank()) {
                return "DISPATCH_TOKEN_REQUIRED";
            }
            if (!expected.equals(request.getDispatchToken())) {
                return "INVALID_DISPATCH_TOKEN";
            }
        }
        if (properties.isRejectOldAttemptCallbacks()) {
            if (request.getAttemptNo() == null) {
                if (properties.isRequireAttemptNo()) {
                    return "ATTEMPT_NO_REQUIRED";
                }
            } else if (request.getAttemptNo() != dispatch.getAttemptCount()) {
                return request.getAttemptNo() < dispatch.getAttemptCount() ? "OLD_ATTEMPT_CALLBACK" : "FUTURE_ATTEMPT_CALLBACK";
            }
        }
        if (properties.isEnforceGatewayAndAgentIdentity()) {
            String identityError = requireMatching("agentId", request.getAgentId(), dispatch.getAgentId());
            if (identityError != null) return identityError;
            identityError = requireMatching("ownerGatewayNodeId", request.getOwnerGatewayNodeId(), dispatch.getOwnerGatewayNodeId());
            if (identityError != null) return identityError;
            identityError = requireMatching("agentSessionId", request.getAgentSessionId(), dispatch.getAgentSessionId());
            if (identityError != null) return identityError;
        }
        String fencingError = validateAssignmentFence(request, dispatch);
        if (fencingError != null) {
            return fencingError;
        }
        if (!properties.isAllowTerminalCallbackOverride()) {
            if (isTerminal(task.getStatus())) {
                return "TASK_ALREADY_TERMINAL";
            }
            if (isTerminal(dispatch.getStatus())) {
                return "DISPATCH_ALREADY_TERMINAL";
            }
        }
        if (properties.isEnforceStateTransition() && !isAllowedDispatchTransition(type, dispatch.getStatus())) {
            return "INVALID_DISPATCH_TRANSITION_" + dispatch.getStatus() + "_TO_" + type;
        }
        return null;
    }


    private String validateAssignmentFence(TaskCallbackRequest request, DispatchRequest dispatch) {
        if (!properties.isEnforceAssignmentFencing()) {
            return null;
        }
        if (assignmentRepository == null) {
            return null;
        }
        String assignmentId = firstNonBlank(request.getAssignmentId(), dispatch == null ? null : dispatch.getAssignmentId());
        if (assignmentId == null || assignmentId.isBlank()) {
            return properties.isRequireAssignmentIdForFencing() ? "ASSIGNMENT_ID_REQUIRED" : null;
        }
        TaskAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return properties.isRequireKnownAssignmentForFencing() ? "ASSIGNMENT_NOT_FOUND" : null;
        }
        AssignmentFencingValidation validation = assignmentFencingTokenPolicy.validate(assignment, assignmentId, request.getFencingToken(), OffsetDateTime.now(ZoneOffset.UTC));
        return validation.accepted() ? null : validation.code();
    }

    private void updateExecutionAttemptFromCallback(TaskCallbackType type, TaskCallbackRequest request) {
        if (executionAttemptService == null || request.getAssignmentId() == null || request.getAssignmentId().isBlank()) {
            return;
        }
        try {
            switch (type) {
                case ACK, PROGRESS -> executionAttemptService.markRunningForAssignment(request.getAssignmentId());
                case RESULT -> {
                    if (resultIndicatesFailure(request)) {
                        executionAttemptService.markFailedForAssignment(request.getAssignmentId(), request.getCallbackId(),
                                firstNonBlank(request.getErrorCode(), "RESULT_FAILED"),
                                firstNonBlank(request.getErrorMessage(), request.getMessage(), request.getResultStatus()));
                    } else {
                        executionAttemptService.markSucceededForAssignment(request.getAssignmentId(), request.getCallbackId(),
                                firstNonBlank(request.getResultStatus(), "SUCCEEDED"));
                    }
                }
                case ERROR -> executionAttemptService.markFailedForAssignment(request.getAssignmentId(), request.getCallbackId(),
                        firstNonBlank(request.getErrorCode(), "AGENT_ERROR"),
                        firstNonBlank(request.getErrorMessage(), request.getMessage()));
            }
        } catch (RuntimeException ignored) {
            // Execution-attempt tracking must not make an already-accepted callback fail.
        }
    }

    private void markStaleExecutionAttempt(TaskCallbackRequest request, String reason) {
        if (executionAttemptService == null || request.getAssignmentId() == null || request.getAssignmentId().isBlank()) {
            return;
        }
        try {
            if ("INVALID_FENCING_TOKEN".equals(reason)
                    || "FENCING_TOKEN_REQUIRED".equals(reason)
                    || "ASSIGNMENT_LEASE_EXPIRED".equals(reason)
                    || "ASSIGNMENT_ID_MISMATCH".equals(reason)) {
                executionAttemptService.markStaleCallbackRejected(request.getAssignmentId(), request.getCallbackId(), reason);
            }
        } catch (RuntimeException ignored) {
            // Idempotent callback rejection must remain safe even if execution-attempt persistence is unavailable.
        }
    }

    private String requireMatching(String field, String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return "EXPECTED_" + field + "_MISSING";
        }
        if (actual == null || actual.isBlank()) {
            return field + "_REQUIRED";
        }
        if (!expected.equals(actual)) {
            return field + "_MISMATCH";
        }
        return null;
    }

    private boolean isAllowedDispatchTransition(TaskCallbackType type, DispatchRequestStatus status) {
        if (status == null) return false;
        return switch (type) {
            case ACK -> status == DispatchRequestStatus.DISPATCHING || status == DispatchRequestStatus.DISPATCHED;
            case PROGRESS -> status == DispatchRequestStatus.ACKED
                    || status == DispatchRequestStatus.RUNNING;
            case RESULT, ERROR -> status == DispatchRequestStatus.DISPATCHING
                    || status == DispatchRequestStatus.DISPATCHED
                    || status == DispatchRequestStatus.ACKED
                    || status == DispatchRequestStatus.RUNNING;
        };
    }

    private boolean isTerminal(TaskStatus status) {
        return status != null && status.isTerminal();
    }

    private boolean isTerminal(DispatchRequestStatus status) {
        return status == DispatchRequestStatus.COMPLETED
                || status == DispatchRequestStatus.FAILED
                || status == DispatchRequestStatus.TIMED_OUT
                || status == DispatchRequestStatus.CANCELLED
                || status == DispatchRequestStatus.REJECTED
                || status == DispatchRequestStatus.DEAD_LETTER;
    }

    private TaskCallbackResult ignore(TaskCallbackRecord record, DispatchRequest dispatch, TaskRecord task, String reason, String message) {
        record.setAccepted(false);
        record.setIgnoredReason(reason);
        record.setNewTaskStatus(task == null ? record.getPreviousTaskStatus() : name(task.getStatus()));
        record.setNewDispatchStatus(dispatch == null ? record.getPreviousDispatchStatus() : name(dispatch.getStatus()));
        callbackRepository.save(record);
        log.warn("callback_inbox_ignored taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={} reason={} message={} previousTaskStatus={} newTaskStatus={} previousDispatchStatus={} newDispatchStatus={}",
                record.getTaskId(), record.getCallbackType(), record.getCallbackId(), record.getDispatchRequestId(), record.getAssignmentId(), record.getAgentId(),
                reason, message, record.getPreviousTaskStatus(), record.getNewTaskStatus(), record.getPreviousDispatchStatus(), record.getNewDispatchStatus());
        return record(resultFrom(record, false, false, reason, message));
    }

    private TaskCallbackResult record(TaskCallbackResult result) {
        metrics.recordCallback(result);
        return result;
    }

    private TaskCallbackRecord recordFrom(TaskCallbackType type, TaskCallbackRequest request, OffsetDateTime now) {
        TaskCallbackRecord record = new TaskCallbackRecord();
        record.setCallbackId(request.getCallbackId());
        record.setCallbackType(type);
        record.setTaskId(request.getTaskId());
        record.setDispatchRequestId(request.getDispatchRequestId());
        record.setAssignmentId(request.getAssignmentId());
        record.setAgentId(request.getAgentId());
        record.setOwnerGatewayNodeId(request.getOwnerGatewayNodeId());
        record.setAgentSessionId(request.getAgentSessionId());
        record.setAttemptNo(request.getAttemptNo());
        record.setFencingToken(request.getFencingToken());
        record.setMessage(request.getMessage());
        record.setProgressPercent(request.getProgressPercent());
        record.setErrorCode(request.getErrorCode());
        record.setErrorMessage(request.getErrorMessage());
        record.setPayload(request.getPayload());
        record.setOccurredAt(request.getOccurredAt());
        record.setProcessedAt(now);
        record.setDuplicate(false);
        record.setIdempotencyKey(idempotencyKey(type, request));
        record.setCallbackFingerprint(callbackFingerprint(type, request));
        record.setReplayDetected(false);
        record.setAccepted(true);
        record.setNewTaskStatus("RESERVED");
        return record;
    }

    private PersistenceWriteResult transitionDispatch(TaskCallbackType type,
                                                       DispatchRequest dispatch,
                                                       TaskCallbackRequest callback,
                                                       OffsetDateTime now) {
        DispatchStatusTransition transition = new DispatchStatusTransition();
        transition.setDispatchRequestId(dispatch.getDispatchRequestId());
        transition.setAllowedCurrentStatuses(allowedDispatchStatuses(type));
        transition.setNewStatus(targetDispatchStatus(type, callback));
        transition.setExpectedAttemptNo(dispatch.getAttemptCount());
        transition.setExpectedDispatchToken(dispatch.getDispatchToken());
        transition.setLastCallbackId(callback.getCallbackId());
        transition.setReason(dispatchReason(type, callback));
        transition.setLastError(dispatchLastError(type, callback));
        transition.setUpdatedAt(now);
        if (type == TaskCallbackType.RESULT && !resultIndicatesFailure(callback)) {
            transition.setCompletedAt(now);
        }
        if (type == TaskCallbackType.RESULT && resultIndicatesFailure(callback)) {
            transition.setFailedAt(now);
        }
        if (type == TaskCallbackType.ERROR) {
            transition.setFailedAt(now);
        }
        return dispatchRepository.transitionStatus(transition);
    }

    private TaskExecutionStateTransition taskTransition(TaskCallbackType type,
                                                        TaskRecord task,
                                                        TaskCallbackRequest request,
                                                        OffsetDateTime now) {
        TaskExecutionStateTransition transition = new TaskExecutionStateTransition();
        transition.setTaskId(task.getTaskId());
        transition.setAllowedCurrentStatuses(openTaskStatuses());
        switch (type) {
            case ACK, PROGRESS -> {
                transition.setNewStatus(TaskStatus.RUNNING);
                transition.setLifecycleReason("Agent callback " + type);
            }
            case RESULT -> {
                transition.setNewStatus(resultIndicatesFailure(request) ? TaskStatus.FAILED : TaskStatus.SUCCEEDED);
                transition.setTerminalAt(now);
                transition.setLifecycleReason(resultIndicatesFailure(request) ? "Agent result failed" : "Agent result completed");
            }
            case ERROR -> {
                transition.setNewStatus(TaskStatus.FAILED);
                transition.setTerminalAt(now);
                transition.setLifecycleReason("Agent error callback" + suffix(firstNonBlank(request.getErrorMessage(), request.getMessage())));
            }
        }
        transition.setUpdatedAt(now);
        return transition;
    }

    private List<TaskStatus> openTaskStatuses() {
        return List.of(TaskStatus.QUEUED, TaskStatus.CREATED, TaskStatus.ASSIGNED, TaskStatus.DISPATCHED, TaskStatus.RUNNING, TaskStatus.RETRY_WAIT, TaskStatus.RECONCILING);
    }

    private List<DispatchRequestStatus> allowedDispatchStatuses(TaskCallbackType type) {
        return switch (type) {
            case ACK -> List.of(DispatchRequestStatus.DISPATCHING, DispatchRequestStatus.DISPATCHED);
            case PROGRESS -> List.of(DispatchRequestStatus.ACKED, DispatchRequestStatus.RUNNING);
            case RESULT, ERROR -> List.of(
                    DispatchRequestStatus.DISPATCHING,
                    DispatchRequestStatus.DISPATCHED,
                    DispatchRequestStatus.ACKED,
                    DispatchRequestStatus.RUNNING);
        };
    }

    private DispatchRequestStatus targetDispatchStatus(TaskCallbackType type, TaskCallbackRequest callback) {
        return switch (type) {
            case ACK -> DispatchRequestStatus.ACKED;
            case PROGRESS -> DispatchRequestStatus.RUNNING;
            case RESULT -> resultIndicatesFailure(callback) ? DispatchRequestStatus.FAILED : DispatchRequestStatus.COMPLETED;
            case ERROR -> DispatchRequestStatus.FAILED;
        };
    }

    private String dispatchReason(TaskCallbackType type, TaskCallbackRequest callback) {
        return switch (type) {
            case ACK -> "Agent acknowledged dispatch" + suffix(callback.getMessage());
            case PROGRESS -> "Agent progress" + progressSuffix(callback);
            case RESULT -> resultIndicatesFailure(callback)
                    ? "Agent result indicates failure" + suffix(dispatchLastError(type, callback))
                    : "Agent completed task" + suffix(callback.getMessage());
            case ERROR -> "Agent error" + suffix(dispatchLastError(type, callback));
        };
    }

    private String dispatchLastError(TaskCallbackType type, TaskCallbackRequest callback) {
        if (type == TaskCallbackType.RESULT && resultIndicatesFailure(callback)) {
            return firstNonBlank(callback.getErrorMessage(), callback.getMessage(), callback.getResultStatus());
        }
        if (type == TaskCallbackType.ERROR) {
            return firstNonBlank(callback.getErrorMessage(), callback.getMessage(), callback.getErrorCode(), "Agent error callback");
        }
        return null;
    }

    private String resolveConcurrentDispatchReason(DispatchRequest latest) {
        if (latest == null) {
            return "DISPATCH_NOT_FOUND";
        }
        if (isTerminal(latest.getStatus())) {
            return "DISPATCH_ALREADY_TERMINAL";
        }
        return "CONCURRENT_STATE_CONFLICT";
    }

    private void applyTaskTransition(TaskCallbackType type, TaskRecord task, TaskCallbackRequest request, OffsetDateTime now) {
        switch (type) {
            case ACK, PROGRESS -> {
                task.setStatus(TaskStatus.RUNNING);
                task.setLifecycleReason("Agent callback " + type);
            }
            case RESULT -> {
                task.setStatus(resultIndicatesFailure(request) ? TaskStatus.FAILED : TaskStatus.SUCCEEDED);
                task.setTerminalAt(now);
                task.setLifecycleReason(resultIndicatesFailure(request) ? "Agent result failed" : "Agent result completed");
            }
            case ERROR -> {
                task.setStatus(TaskStatus.FAILED);
                task.setTerminalAt(now);
                task.setLifecycleReason("Agent error callback" + suffix(firstNonBlank(request.getErrorMessage(), request.getMessage())));
            }
        }
        task.setUpdatedAt(now);
    }

    private void applyDispatchTransition(TaskCallbackType type, DispatchRequest request, TaskCallbackRequest callback, OffsetDateTime now) {
        switch (type) {
            case ACK -> {
                request.setStatus(DispatchRequestStatus.ACKED);
                request.setReason("Agent acknowledged dispatch" + suffix(callback.getMessage()));
            }
            case PROGRESS -> {
                request.setStatus(DispatchRequestStatus.RUNNING);
                request.setReason("Agent progress" + progressSuffix(callback));
            }
            case RESULT -> {
                if (resultIndicatesFailure(callback)) {
                    request.setStatus(DispatchRequestStatus.FAILED);
                    request.setFailedAt(now);
                    request.setLastError(firstNonBlank(callback.getErrorMessage(), callback.getMessage(), callback.getResultStatus()));
                    request.setReason("Agent result indicates failure" + suffix(request.getLastError()));
                } else {
                    request.setStatus(DispatchRequestStatus.COMPLETED);
                    request.setCompletedAt(now);
                    request.setReason("Agent completed task" + suffix(callback.getMessage()));
                }
            }
            case ERROR -> {
                request.setStatus(DispatchRequestStatus.FAILED);
                request.setFailedAt(now);
                request.setLastError(firstNonBlank(callback.getErrorMessage(), callback.getMessage(), callback.getErrorCode(), "Agent error callback"));
                request.setReason("Agent error" + suffix(request.getLastError()));
            }
        }
        request.setLastCallbackId(callback.getCallbackId());
        request.setUpdatedAt(now);
        request.setClaimedBy(null);
        request.setClaimStartedAt(null);
        request.setClaimUntil(null);
    }

    private boolean resultIndicatesFailure(TaskCallbackRequest request) {
        String status = request.getResultStatus();
        return status != null && (status.equalsIgnoreCase("FAILED") || status.equalsIgnoreCase("ERROR"));
    }

    private TaskCallbackResult resultFrom(TaskCallbackRecord record, boolean duplicate, boolean accepted, String ignoredReason, String message) {
        TaskCallbackResult result = new TaskCallbackResult();
        result.setCallbackId(record.getCallbackId());
        result.setTaskId(record.getTaskId());
        result.setDispatchRequestId(record.getDispatchRequestId());
        result.setCallbackType(record.getCallbackType() == null ? null : record.getCallbackType().name());
        result.setDuplicate(duplicate);
        result.setAccepted(accepted);
        result.setTaskStatus(record.getNewTaskStatus());
        result.setDispatchStatus(record.getNewDispatchStatus());
        result.setIgnoredReason(ignoredReason);
        TaskCallbackErrorCode errorCode = accepted
                ? (duplicate ? TaskCallbackErrorCode.DUPLICATE_CALLBACK : TaskCallbackErrorCode.NONE)
                : TaskCallbackErrorCode.fromIgnoredReason(ignoredReason);
        result.setErrorCode(errorCode.name());
        result.setHttpStatus(errorCode.getHttpStatus());
        result.setRetryable(errorCode.isRetryable());
        result.setMessage(message == null || message.isBlank() ? errorCode.getDescription() : message);
        return result;
    }


    private boolean isTerminalCallback(TaskCallbackType type) {
        return type == TaskCallbackType.RESULT || type == TaskCallbackType.ERROR;
    }

    private boolean isReplayMismatch(TaskCallbackRecord current, TaskCallbackRecord previous) {
        if (!properties.isReplayProtectionEnabled() || !properties.isRejectCallbackIdReplayMismatch()) {
            return false;
        }
        String currentFingerprint = current == null ? null : current.getCallbackFingerprint();
        String previousFingerprint = previous == null ? null : previous.getCallbackFingerprint();
        if (currentFingerprint == null || currentFingerprint.isBlank()
                || previousFingerprint == null || previousFingerprint.isBlank()) {
            return false;
        }
        return !Objects.equals(currentFingerprint, previousFingerprint);
    }

    private String idempotencyKey(TaskCallbackType type, TaskCallbackRequest request) {
        String raw = String.join("|",
                "TASK_CALLBACK_IDEMPOTENCY_V2",
                type == null ? "" : type.name(),
                safe(request.getTaskId()),
                safe(request.getDispatchRequestId()),
                safe(request.getAssignmentId()),
                safe(request.getAgentId()),
                safe(request.getOwnerGatewayNodeId()),
                safe(request.getAgentSessionId()),
                safe(request.getAttemptNo()),
                safe(request.getCallbackId()));
        return sha256("idk-", raw);
    }

    private String callbackFingerprint(TaskCallbackType type, TaskCallbackRequest request) {
        String raw = String.join("|",
                "TASK_CALLBACK_FINGERPRINT_V2",
                type == null ? "" : type.name(),
                safe(request.getTaskId()),
                safe(request.getDispatchRequestId()),
                safe(request.getAssignmentId()),
                safe(request.getAgentId()),
                safe(request.getOwnerGatewayNodeId()),
                safe(request.getAgentSessionId()),
                safe(request.getAttemptNo()),
                secretFingerprint(request.getDispatchToken()),
                secretFingerprint(request.getFencingToken()),
                safe(request.getProgressPercent()),
                safe(request.getResultStatus()),
                safe(request.getErrorCode()),
                safe(request.getErrorMessage()),
                safe(request.getMessage()),
                canonical(request.getPayload()));
        return sha256("cbf-", raw);
    }

    private String secretFingerprint(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        return sha256("sec-", secret);
    }

    private String sha256(String prefix, String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return prefix + HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return prefix + Math.abs(raw.hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    private String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted((a, b) -> String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey())))
                    .map(e -> String.valueOf(e.getKey()) + "=" + canonical(e.getValue()))
                    .toList()
                    .toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(canonical(item));
                first = false;
            }
            return builder.append(']').toString();
        }
        return String.valueOf(value);
    }

    private String generatedCallbackId(TaskCallbackType type, TaskCallbackRequest request) {
        String raw = String.join("|",
                type.name(),
                safe(request.getTaskId()),
                safe(request.getDispatchRequestId()),
                safe(request.getAssignmentId()),
                safe(request.getAgentId()),
                safe(request.getOwnerGatewayNodeId()),
                safe(request.getAgentSessionId()),
                safe(request.getAttemptNo()),
                safe(request.getFencingToken()),
                safe(request.getProgressPercent()),
                safe(request.getResultStatus()),
                safe(request.getErrorCode()),
                safe(request.getErrorMessage()),
                safe(request.getMessage()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "cb-" + HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "cb-" + Math.abs(raw.hashCode());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String suffix(String message) {
        return message == null || message.isBlank() ? "" : ": " + message;
    }

    private String progressSuffix(TaskCallbackRequest callback) {
        String progress = callback.getProgressPercent() == null ? "" : " " + callback.getProgressPercent() + "%";
        return progress + suffix(callback.getMessage());
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
