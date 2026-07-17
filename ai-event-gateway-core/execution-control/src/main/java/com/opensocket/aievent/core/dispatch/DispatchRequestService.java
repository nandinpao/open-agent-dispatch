package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskDispatchPort;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.events.DispatchDeadLetteredEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;

@Service
public class DispatchRequestService implements TaskDispatchPort {
    private static final Logger log = LoggerFactory.getLogger(DispatchRequestService.class);

    private final DispatchRequestRepository repository;
    private final DispatchEligibilityService eligibilityService;
    private final DispatchProperties properties;

    @Autowired(required = false)
    private ModuleEventPublisher eventPublisher = ModuleEventPublisher.noop();

    @Autowired(required = false)
    private DispatchAttemptHistoryService attemptHistoryService;

    public DispatchRequestService(DispatchRequestRepository repository,
                                  DispatchEligibilityService eligibilityService,
                                  DispatchProperties properties) {
        this.repository = repository;
        this.eligibilityService = eligibilityService;
        this.properties = properties;
    }

    @Override
    public DispatchDecisionResult createIfEligible(TaskAssignment assignment, TaskRecord task) {
        if (assignment == null) {
            return DispatchDecisionResult.none("No assignment is available for dispatch review");
        }
        return repository.findOpenByAssignmentId(assignment.getAssignmentId())
                .map(existing -> {
                    log.info("dispatch_request_existing assignmentId={} taskId={} dispatchRequestId={} status={} agentId={} gatewayNode={} reason={}",
                            safe(assignment.getAssignmentId()), safe(assignment.getTaskId()), safe(existing.getDispatchRequestId()),
                            existing.getStatus(), safe(existing.getAgentId()), safe(existing.getOwnerGatewayNodeId()), safe(existing.getReason()));
                    return new DispatchDecisionResult(false,
                            existing.getDispatchRequestId(),
                            existing.getStatus() == null ? null : existing.getStatus().name(),
                            existing.getReviewMode() == null ? null : existing.getReviewMode().name(),
                            existing.getEligibilityStatus() == null ? null : existing.getEligibilityStatus().name(),
                            existing.getGatewayDispatchPath(),
                            "Assignment already has open dispatch request " + existing.getDispatchRequestId());
                })
                .orElseGet(() -> createNew(assignment, task));
    }

    public DispatchRequest approve(String dispatchRequestId, String reason) {
        DispatchRequest request = repository.findById(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        request.setStatus(DispatchRequestStatus.APPROVED);
        request.setReason(reason == null || reason.isBlank() ? queuedReason() : reason);
        request.setApprovedAt(now);
        request.setUpdatedAt(now);
        return repository.save(request);
    }

    public DispatchRequest reject(String dispatchRequestId, String reason) {
        DispatchRequest request = repository.findById(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        request.setStatus(DispatchRequestStatus.REJECTED);
        request.setReason(reason == null || reason.isBlank() ? "Rejected by reviewer" : reason);
        request.setUpdatedAt(now);
        return repository.save(request);
    }

    public DispatchRequest retry(String dispatchRequestId, String reason, boolean resetAttempts, boolean immediate) {
        DispatchRequest request = repository.findById(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if (request.getStatus() != DispatchRequestStatus.DEAD_LETTER
                && request.getStatus() != DispatchRequestStatus.FAILED
                && request.getStatus() != DispatchRequestStatus.TIMED_OUT
                && request.getStatus() != DispatchRequestStatus.RETRY_WAITING) {
            throw new IllegalStateException("Only DEAD_LETTER, FAILED, TIMED_OUT or RETRY_WAITING dispatch request can be retried: " + dispatchRequestId);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        request.setStatus(immediate ? DispatchRequestStatus.APPROVED : DispatchRequestStatus.RETRY_WAITING);
        request.setReason(reason == null || reason.isBlank() ? (immediate ? queuedReason() : "Dispatch retry scheduled") : reason);
        request.setUpdatedAt(now);
        request.setFailedAt(null);
        request.setDeadLetterAt(null);
        request.setTimedOutAt(null);
        request.setLastError(null);
        if (resetAttempts) {
            request.setAttemptCount(0);
        }
        if (immediate) {
            request.setRetryWaitingAt(null);
            request.setNextRetryAt(null);
            request.setApprovedAt(now);
        } else {
            request.setRetryWaitingAt(now);
            request.setNextRetryAt(now.plus(properties.getRetry().getInitialBackoff()));
        }
        if (request.getCommand() != null) {
            request.getCommand().setAttemptNo(request.getAttemptCount() + 1);
        }
        return repository.save(request);
    }

    @Transactional
    public DispatchRequest moveToDeadLetter(String dispatchRequestId, String reason) {
        DispatchRequest request = repository.findById(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if (request.getStatus() == DispatchRequestStatus.DEAD_LETTER) {
            return request;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        request.setStatus(DispatchRequestStatus.DEAD_LETTER);
        request.setReason(reason == null || reason.isBlank() ? "Moved to dead letter by reviewer" : reason);
        request.setLastError(request.getReason());
        request.setFailedAt(now);
        request.setDeadLetterAt(now);
        request.setUpdatedAt(now);
        DispatchRequest saved = repository.save(request);
        eventPublisher.publish(new DispatchDeadLetteredEvent(
                "dispatch-dead-letter-" + saved.getDispatchRequestId() + "-" + saved.getAttemptCount(), saved.getDispatchRequestId(), saved.getAssignmentId(),
                saved.getTaskId(), saved.getIncidentId(), saved.getAgentId(), saved.getAttemptCount(),
                saved.getReason(), now));
        return saved;
    }

    public DispatchRequest cancel(String dispatchRequestId, String reason) {
        DispatchRequest request = repository.findById(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if (request.getStatus() == DispatchRequestStatus.COMPLETED
                || request.getStatus() == DispatchRequestStatus.FAILED
                || request.getStatus() == DispatchRequestStatus.TIMED_OUT
                || request.getStatus() == DispatchRequestStatus.DEAD_LETTER
                || request.getStatus() == DispatchRequestStatus.CANCELLED) {
            throw new IllegalStateException("Dispatch request is already terminal: " + dispatchRequestId + " status=" + request.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        request.setStatus(DispatchRequestStatus.CANCELLED);
        request.setReason(reason == null || reason.isBlank() ? "Cancelled by reviewer" : reason);
        request.setUpdatedAt(now);
        return repository.save(request);
    }

    private DispatchDecisionResult createNew(TaskAssignment assignment, TaskRecord task) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DispatchEligibilityService.EligibilityResult eligibility = eligibilityService.check(assignment, task);
        DispatchRequest request = new DispatchRequest();
        request.setDispatchRequestId("dispatch-" + UUID.randomUUID());
        request.setAssignmentId(assignment.getAssignmentId());
        request.setTaskId(assignment.getTaskId());
        request.setIncidentId(assignment.getIncidentId());
        request.setAgentId(assignment.getAgentId());
        request.setOwnerGatewayNodeId(assignment.getOwnerGatewayNodeId());
        request.setAgentSessionId(assignment.getAgentSessionId());
        request.setSiteId(assignment.getSiteId());
        request.setReviewMode(properties.getReviewMode());
        request.setEligibilityStatus(eligibility.eligible() ? DispatchEligibilityStatus.ELIGIBLE : DispatchEligibilityStatus.NOT_ELIGIBLE);
        request.setDispatchMethod(DispatchMethod.INTERNAL_GATEWAY_HTTP);
        request.setGatewayDispatchPath(properties.getGatewayDispatchPath());
        request.setDispatchToken("dispatch-token-" + UUID.randomUUID());
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setCommand(command(request, task, assignment));
        if (!eligibility.eligible()) {
            request.setStatus(DispatchRequestStatus.SUPPRESSED);
            request.setReason(eligibility.reason());
        } else if (autoQueueDispatch()) {
            request.setStatus(DispatchRequestStatus.APPROVED);
            request.setApprovedAt(now);
            request.setReason(queuedReason());
        } else {
            request.setStatus(DispatchRequestStatus.PENDING_REVIEW);
            request.setReason("Pending explicit dispatch review before gateway delivery");
        }
        DispatchRequest saved = repository.save(request);
        log.info("dispatch_request_created dispatchRequestId={} taskId={} assignmentId={} agentId={} status={} reviewMode={} eligibility={} executionPolicy={} clientEnabled={} autoExecuteIntervalMs={} gatewayBaseUrl={} gatewayNode={} gatewayPath={} reason={}",
                safe(saved.getDispatchRequestId()), safe(saved.getTaskId()), safe(saved.getAssignmentId()), safe(saved.getAgentId()),
                saved.getStatus(), saved.getReviewMode(), saved.getEligibilityStatus(), properties.getExecutionPolicy(),
                properties.getClient().isEnabled(), properties.getClient().getMaxBatchSize(), safe(properties.getClient().getDefaultGatewayBaseUrl()),
                safe(saved.getOwnerGatewayNodeId()), safe(saved.getGatewayDispatchPath()), safe(saved.getReason()));
        recordDispatchRequestCreated(saved);
        return new DispatchDecisionResult(true,
                saved.getDispatchRequestId(),
                saved.getStatus() == null ? null : saved.getStatus().name(),
                saved.getReviewMode() == null ? null : saved.getReviewMode().name(),
                saved.getEligibilityStatus() == null ? null : saved.getEligibilityStatus().name(),
                saved.getGatewayDispatchPath(),
                saved.getReason());
    }


    private boolean autoQueueDispatch() {
        DispatchReviewMode reviewMode = properties.getReviewMode();
        return reviewMode == DispatchReviewMode.NOT_REQUIRED || reviewMode == DispatchReviewMode.AUTO_APPROVE;
    }

    private String queuedReason() {
        if (!properties.getClient().isEnabled()) {
            return "Dispatch request is approved but gateway delivery is blocked because dispatch client is disabled";
        }
        if (properties.getExecutionPolicy() == DispatchExecutionPolicy.PAUSED) {
            return "Dispatch request is approved but gateway delivery is paused by dispatch execution policy";
        }
        if (properties.getExecutionPolicy() == DispatchExecutionPolicy.MANUAL_HOLD) {
            return "Dispatch request is approved and waiting for an explicit operator execution trigger";
        }
        return "Dispatch request queued for automatic gateway delivery";
    }

    private void recordDispatchRequestCreated(DispatchRequest request) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordDispatchRequestCreated(request, request == null ? null : request.getReason(), request == null ? null : request.getCreatedAt());
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private NettyDispatchCommand command(DispatchRequest request, TaskRecord task, TaskAssignment assignment) {
        NettyDispatchCommand command = new NettyDispatchCommand();
        command.setTaskId(request.getTaskId());
        command.setAssignmentId(request.getAssignmentId());
        command.setDispatchRequestId(request.getDispatchRequestId());
        command.setAttemptNo(1);
        command.setTargetAgentId(request.getAgentId());
        command.setOwnerGatewayNodeId(request.getOwnerGatewayNodeId());
        command.setAgentSessionId(request.getAgentSessionId());
        command.setSourceNodeId(properties.getSourceNodeId());
        command.setDispatchToken(request.getDispatchToken());
        // Assignment fencing is separate from the dispatch token. Agents must echo
        // this token on ACK/RESULT callbacks so Core can prove the callback belongs
        // to the current assignment lease rather than a stale redispatch attempt.
        command.setFencingToken(assignment == null ? null : assignment.getFencingToken());
        command.setIncidentId(request.getIncidentId());
        if (task != null) {
            command.setTaskType(task.getTaskType() == null ? null : task.getTaskType().name());
            command.setPriority(task.getPriority() == null ? null : task.getPriority().name());
            command.setRoutingPolicy(task.getRoutingPolicy());
            command.setRequiredCapabilities(task.getRequiredCapabilities());
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("incidentId", task.getIncidentId());
            input.put("sourceEventId", task.getSourceEventId());
            input.put("tenantId", task.getTenantId());
            input.put("sourceSystem", task.getSourceSystem());
            input.put("siteId", task.getSiteId());
            input.put("plantId", task.getPlantId());
            input.put("objectType", task.getObjectType());
            input.put("objectId", task.getObjectId());
            input.put("eventType", task.getEventType());
            input.put("errorCode", task.getErrorCode());
            input.put("priority", task.getPriority() == null ? null : task.getPriority().name());
            input.put("routingPolicy", task.getRoutingPolicy());
            input.put("requiredCapabilities", task.getRequiredCapabilities());
            input.put("externalExecutionKey", task.getExternalExecutionKey());
            command.setInput(input);
        }
        return command;
    }
}
