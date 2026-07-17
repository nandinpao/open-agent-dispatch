package com.opensocket.aievent.core.dispatch;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.events.DispatchDeadLetteredEvent;
import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

@Service
public class DispatchExecutionService {
    private static final Logger log = LoggerFactory.getLogger(DispatchExecutionService.class);

    private final DispatchRequestRepository dispatchRepository;
    private final TaskOrchestrationFacade taskOrchestrationFacade;
    private final NettyDispatchPort nettyDispatchPort;
    private final DispatchProperties properties;
    private final ExecutionMetricsPort metrics;
    private final ModuleEventPublisher eventPublisher;
    private final AgentDirectoryFacade agentDirectory;

    @Autowired(required = false)
    private DispatchAttemptHistoryService attemptHistoryService;

    public DispatchExecutionService(
            DispatchRequestRepository dispatchRepository,
            TaskOrchestrationFacade taskOrchestrationFacade,
            NettyDispatchPort nettyDispatchPort,
            DispatchProperties properties) {
        this(
                dispatchRepository,
                taskOrchestrationFacade,
                nettyDispatchPort,
                properties,
                ExecutionMetricsPort.noop(),
                ModuleEventPublisher.noop(),
                null);
    }

    @Autowired
    public DispatchExecutionService(
            DispatchRequestRepository dispatchRepository,
            TaskOrchestrationFacade taskOrchestrationFacade,
            NettyDispatchPort nettyDispatchPort,
            DispatchProperties properties,
            ObjectProvider<AgentDirectoryFacade> agentDirectoryProvider,
            ObjectProvider<ExecutionMetricsPort> metricsProvider,
            ObjectProvider<ModuleEventPublisher> eventPublisherProvider) {
        this(
                dispatchRepository,
                taskOrchestrationFacade,
                nettyDispatchPort,
                properties,
                metricsProvider.getIfAvailable(ExecutionMetricsPort::noop),
                eventPublisherProvider.getIfAvailable(ModuleEventPublisher::noop),
                agentDirectoryProvider.getIfAvailable());
    }

    /** Compatibility/testing constructor with explicit ports. */
    public DispatchExecutionService(
            DispatchRequestRepository dispatchRepository,
            TaskOrchestrationFacade taskOrchestrationFacade,
            NettyDispatchPort nettyDispatchPort,
            DispatchProperties properties,
            ExecutionMetricsPort metrics,
            ModuleEventPublisher eventPublisher) {
        this(
                dispatchRepository,
                taskOrchestrationFacade,
                nettyDispatchPort,
                properties,
                metrics,
                eventPublisher,
                null);
    }

    public DispatchExecutionService(
            DispatchRequestRepository dispatchRepository,
            TaskOrchestrationFacade taskOrchestrationFacade,
            NettyDispatchPort nettyDispatchPort,
            DispatchProperties properties,
            ExecutionMetricsPort metrics,
            ModuleEventPublisher eventPublisher,
            AgentDirectoryFacade agentDirectory) {
        this.dispatchRepository = dispatchRepository;
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.nettyDispatchPort = nettyDispatchPort;
        this.properties = properties;
        this.metrics = metrics == null ? ExecutionMetricsPort.noop() : metrics;
        this.eventPublisher = eventPublisher == null ? ModuleEventPublisher.noop() : eventPublisher;
        this.agentDirectory = agentDirectory;
    }

    /**
     * Claims the row in one atomic database statement, commits that claim, and only then calls the
     * external gateway. No database transaction is held while the HTTP request is in flight.
     */
    public DispatchExecutionResult execute(String dispatchRequestId) {
        long startedAt = System.nanoTime();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClaimRequest claimRequest = claimRequest(now, 1);
        return dispatchRepository.claimById(dispatchRequestId, claimRequest)
                .map(request -> executeClaimed(request, ownership(request), startedAt))
                .orElseGet(() -> {
                    DispatchRequest current = dispatchRepository.findById(dispatchRequestId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Dispatch request not found: " + dispatchRequestId));
                    return record(
                            DispatchExecutionResult.skipped(
                                    current,
                                    "Dispatch request is not claimable. Current status=" + current.getStatus()),
                            startedAt);
                });
    }

    public List<DispatchExecutionResult> executeApproved(int limit) {
        int capped = Math.max(1, Math.min(limit, properties.getClient().getMaxBatchSize()));
        log.debug("dispatch_execute_approved_scan_started limit={} capped={} clientEnabled={} executionPolicy={} gatewayBaseUrl={} workerId={}",
                limit, capped, properties.getClient().isEnabled(), properties.getExecutionPolicy(), safe(properties.getClient().getDefaultGatewayBaseUrl()), safe(properties.getWorkerId()));
        List<DispatchExecutionResult> results = new ArrayList<>(capped);
        for (int index = 0; index < capped; index++) {
            ClaimRequest claimRequest = claimRequest(OffsetDateTime.now(ZoneOffset.UTC), 1);
            List<DispatchRequest> claimed = dispatchRepository.claimExecutable(claimRequest);
            if (claimed.isEmpty()) {
                if (index == 0) {
                    log.debug("dispatch_execute_approved_no_claimable workerId={} limit={}", safe(properties.getWorkerId()), capped);
                }
                break;
            }
            DispatchRequest request = claimed.getFirst();
            log.info("dispatch_request_claimed dispatchRequestId={} taskId={} assignmentId={} agentId={} status={} attemptCount={} gatewayNode={} gatewayPath={} claimUntil={}",
                    safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAssignmentId()), safe(request.getAgentId()),
                    request.getStatus(), request.getAttemptCount(), safe(request.getOwnerGatewayNodeId()), safe(request.getGatewayDispatchPath()), request.getClaimUntil());
            results.add(executeClaimed(request, ownership(request), System.nanoTime()));
        }
        return List.copyOf(results);
    }

    private DispatchExecutionResult executeClaimed(
            DispatchRequest request,
            ClaimOwnership ownership,
            long startedAt) {
        if (request.getCommand() != null) {
            request.getCommand().setAttemptNo(request.getAttemptCount());
        }
        recordAttemptStarted(request);
        log.info("dispatch_delivery_attempt_started dispatchRequestId={} taskId={} assignmentId={} agentId={} attemptCount={} gatewayNode={} gatewayPath={} clientEnabled={} gatewayBaseUrl={}",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAssignmentId()), safe(request.getAgentId()),
                request.getAttemptCount(), safe(request.getOwnerGatewayNodeId()), safe(request.getGatewayDispatchPath()), properties.getClient().isEnabled(),
                safe(properties.getClient().getDefaultGatewayBaseUrl()));

        GatewayDispatchResult gatewayResult;
        try {
            gatewayResult = nettyDispatchPort.dispatch(request);
        } catch (RuntimeException exception) {
            gatewayResult = GatewayDispatchResult.failure(
                    0,
                    "GATEWAY_DISPATCH_EXCEPTION",
                    rootMessage(exception));
        }
        if (gatewayResult == null) {
            gatewayResult = GatewayDispatchResult.failure(
                    0,
                    "NULL_GATEWAY_RESPONSE",
                    "Netty dispatch client returned null");
        }

        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        log.info("dispatch_delivery_attempt_result dispatchRequestId={} taskId={} agentId={} success={} gatewayStatus={} message={}",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAgentId()), gatewayResult.success(),
                safe(gatewayResult.gatewayStatus()), safe(gatewayResult.message()));
        if (gatewayResult.success()) {
            request.setStatus(DispatchRequestStatus.DISPATCHED);
            request.setDispatchedAt(completedAt);
            request.setUpdatedAt(completedAt);
            request.setLastError(null);
            request.setReason("Netty dispatch accepted: " + safe(gatewayResult.message()));
            PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
            if (!write.applied()) {
                log.warn("dispatch_delivery_claim_lost dispatchRequestId={} taskId={} agentId={} gatewayStatus={} writeResult={}",
                        safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAgentId()), safe(gatewayResult.gatewayStatus()), write);
                return record(claimLostResult(request, gatewayResult, write), startedAt);
            }
            clearClaim(request);
            clearRuntimeBackoffAfterSuccessfulDispatch(request);
            updateTaskDispatched(request, completedAt);
            recordGatewayDelivered(request, gatewayResult, completedAt);

            DispatchExecutionResult result = DispatchExecutionResult.from(request);
            result.setExecuted(true);
            result.setGatewayStatus(gatewayResult.gatewayStatus());
            result.setTaskStatus(TaskStatus.DISPATCHED.name());
            result.setMessage(gatewayResult.message());
            log.info("dispatch_delivery_marked_dispatched dispatchRequestId={} taskId={} agentId={} gatewayStatus={} taskStatus={}",
                    safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAgentId()), safe(gatewayResult.gatewayStatus()), TaskStatus.DISPATCHED.name());
            return record(result, startedAt);
        }

        String error = gatewayResult.gatewayStatus() + ": " + safe(gatewayResult.message());
        log.warn("dispatch_delivery_failed dispatchRequestId={} taskId={} agentId={} attemptCount={} gatewayStatus={} error={}",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAgentId()), request.getAttemptCount(), safe(gatewayResult.gatewayStatus()), safe(error));
        if (shouldRequeueAfterRuntimeFailure(request, gatewayResult)) {
            return requeueAfterRuntimeFailure(request, ownership, gatewayResult, completedAt, error, startedAt);
        }
        if (properties.getRetry().isEnabled()
                && request.getAttemptCount() < properties.getRetry().getMaxAttempts()) {
            scheduleRetry(request, completedAt, error);
            recordRetryWaiting(request, gatewayResult, completedAt);
            PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
            if (!write.applied()) {
                log.warn("dispatch_delivery_claim_lost dispatchRequestId={} taskId={} agentId={} gatewayStatus={} writeResult={}",
                        safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAgentId()), safe(gatewayResult.gatewayStatus()), write);
                return record(claimLostResult(request, gatewayResult, write), startedAt);
            }
            clearClaim(request);
            updateTaskRetryWaiting(request, completedAt);

            DispatchExecutionResult result = DispatchExecutionResult.from(request);
            result.setExecuted(false);
            result.setGatewayStatus(gatewayResult.gatewayStatus());
            result.setTaskStatus(TaskStatus.RETRY_WAIT.name());
            result.setMessage(
                    "Netty dispatch failed; retry scheduled at "
                            + request.getNextRetryAt()
                            + ". "
                            + safe(gatewayResult.message()));
            return record(result, startedAt);
        }

        request.setStatus(DispatchRequestStatus.DEAD_LETTER);
        request.setFailedAt(completedAt);
        request.setDeadLetterAt(completedAt);
        request.setUpdatedAt(completedAt);
        request.setLastError(error);
        request.setReason("Netty dispatch failed and max attempts reached: " + error);
        PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
        if (!write.applied()) {
            return record(claimLostResult(request, gatewayResult, write), startedAt);
        }
        clearClaim(request);
        publishDeadLetter(request, completedAt);
        updateTaskDeadLetter(request, completedAt, error);
        recordDeadLettered(request, gatewayResult, completedAt);

        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setGatewayStatus(gatewayResult.gatewayStatus());
        result.setTaskStatus(TaskStatus.DEAD_LETTER.name());
        result.setMessage(gatewayResult.message());
        return record(result, startedAt);
    }

    private ClaimRequest claimRequest(OffsetDateTime now, int limit) {
        return ClaimRequest.forLease(
                properties.getWorkerId(),
                now,
                effectiveClaimLease(),
                limit);
    }


    private Duration effectiveClaimLease() {
        Duration configured = properties.getClaimLease();
        Duration minimum = properties.getClient().getConnectTimeout()
                .plus(properties.getClient().getRequestTimeout())
                .plusSeconds(5);
        return configured.compareTo(minimum) >= 0 ? configured : minimum;
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }

    private ClaimOwnership ownership(DispatchRequest request) {
        return new ClaimOwnership(request.getClaimedBy(), request.getClaimUntil());
    }

    private DispatchExecutionResult claimLostResult(
            DispatchRequest attempted,
            GatewayDispatchResult gatewayResult,
            PersistenceWriteResult write) {
        DispatchRequest current = dispatchRepository.findById(attempted.getDispatchRequestId())
                .orElse(attempted);
        if (gatewayResult != null && gatewayResult.success()) {
            recordGatewayDeliveredUnconfirmed(attempted, gatewayResult, write, OffsetDateTime.now(ZoneOffset.UTC));
        }
        DispatchExecutionResult result = DispatchExecutionResult.from(current);
        result.setExecuted(isAcceptedOrLater(current.getStatus()) || (gatewayResult != null && gatewayResult.success()));
        result.setGatewayStatus(gatewayResult == null ? null : gatewayResult.gatewayStatus());
        result.setMessage(
                "Dispatch claim ownership was lost before final persistence. outcome="
                        + write.outcome()
                        + ", currentStatus="
                        + current.getStatus()
                        + (gatewayResult != null && gatewayResult.success()
                        ? "; Netty accepted the command, so callback reconciliation must rely on dispatchToken and attemptNo."
                        : ""));
        return result;
    }

    private boolean isAcceptedOrLater(DispatchRequestStatus status) {
        return status == DispatchRequestStatus.DISPATCHED
                || status == DispatchRequestStatus.ACKED
                || status == DispatchRequestStatus.RUNNING
                || status == DispatchRequestStatus.COMPLETED;
    }

    private boolean shouldRequeueAfterRuntimeFailure(DispatchRequest request, GatewayDispatchResult gatewayResult) {
        if (!properties.getFailureRequeue().isEnabled()
                || agentDirectory == null
                || taskOrchestrationFacade == null
                || request == null
                || !isRuntimeDeliveryFailure(gatewayResult)
                || blank(request.getAssignmentId())
                || blank(request.getTaskId())) {
            return false;
        }
        TaskRecord task = taskOrchestrationFacade.findTask(request.getTaskId()).orElse(null);
        if (task == null || isTerminalTask(task.getStatus())) {
            return false;
        }
        return task.getReassignmentCount() < properties.getFailureRequeue().getMaxReassignments();
    }

    private DispatchExecutionResult requeueAfterRuntimeFailure(
            DispatchRequest request,
            ClaimOwnership ownership,
            GatewayDispatchResult gatewayResult,
            OffsetDateTime now,
            String error,
            long startedAt) {
        OffsetDateTime backoffUntil = applyRuntimeBackoff(request, gatewayResult, now);
        String reason = "Netty runtime delivery failed; task will be requeued for another routing decision"
                + (backoffUntil == null ? "" : "; failed agent backoffUntil=" + backoffUntil)
                + ": " + error;
        request.setStatus(DispatchRequestStatus.FAILED);
        request.setFailedAt(now);
        request.setUpdatedAt(now);
        request.setLastError(error);
        request.setReason(reason);
        PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
        if (!write.applied()) {
            return record(claimLostResult(request, gatewayResult, write), startedAt);
        }
        clearClaim(request);

        recordRuntimeDeliveryFailed(request, gatewayResult, now);
        recordRuntimeBackoffApplied(request, gatewayResult, backoffUntil, now);
        TaskRecord task = taskOrchestrationFacade.reassignTask(
                request.getTaskId(),
                "Dispatch runtime failure requeue; previousAssignment=" + request.getAssignmentId()
                        + "; failedAgent=" + request.getAgentId()
                        + "; reason=" + safe(gatewayResult.message()),
                now);
        recordTaskRequeued(request, task, reason, now);

        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setGatewayStatus(gatewayResult.gatewayStatus());
        result.setTaskStatus(task == null || task.getStatus() == null ? null : task.getStatus().name());
        result.setMessage(reason);
        return record(result, startedAt);
    }


    private void recordAttemptStarted(DispatchRequest request) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordAttemptStarted(request, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    private void recordGatewayDelivered(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime occurredAt) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordGatewayDelivered(request, gatewayResult, occurredAt);
        }
    }

    private void recordGatewayDeliveredUnconfirmed(DispatchRequest request, GatewayDispatchResult gatewayResult, PersistenceWriteResult write, OffsetDateTime occurredAt) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordGatewayDeliveredUnconfirmed(request, gatewayResult, write == null ? null : write.outcome().name(), occurredAt);
        }
    }

    private void recordRetryWaiting(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime occurredAt) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordRetryWaiting(request, gatewayResult, request.getNextRetryAt(), occurredAt);
        }
    }

    private void recordRuntimeDeliveryFailed(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime occurredAt) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordRuntimeDeliveryFailed(request, gatewayResult, occurredAt);
        }
    }

    private void recordRuntimeBackoffApplied(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime backoffUntil, OffsetDateTime occurredAt) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordRuntimeBackoffApplied(request, gatewayResult, backoffUntil, occurredAt);
        }
    }

    private void recordTaskRequeued(DispatchRequest request, TaskRecord task, String reason, OffsetDateTime occurredAt) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordTaskRequeued(request, task, reason, occurredAt);
        }
    }

    private void recordDeadLettered(DispatchRequest request, GatewayDispatchResult gatewayResult, OffsetDateTime occurredAt) {
        if (attemptHistoryService != null) {
            attemptHistoryService.recordDeadLettered(request, gatewayResult, occurredAt);
        }
    }

    private void clearRuntimeBackoffAfterSuccessfulDispatch(DispatchRequest request) {
        if (agentDirectory == null || request == null || blank(request.getAgentId())) {
            return;
        }
        agentDirectory.clearRuntimeBackoff(
                request.getAgentId(),
                "Dispatch delivery succeeded for dispatchRequestId=" + request.getDispatchRequestId());
    }

    private OffsetDateTime applyRuntimeBackoff(
            DispatchRequest request,
            GatewayDispatchResult gatewayResult,
            OffsetDateTime now) {
        if (agentDirectory == null || request == null || blank(request.getAgentId())) {
            return null;
        }
        int nextFailureCount = agentDirectory.findById(request.getAgentId())
                .map(AgentSnapshot::getRuntimeFailureCount)
                .orElse(0) + 1;
        boolean poisonAgent = nextFailureCount >= properties.getFailureRequeue().getPoisonAgentFailureThreshold();
        Duration backoff = computeRuntimeBackoff(nextFailureCount, request.getAgentId());
        OffsetDateTime backoffUntil = now.plus(backoff);
        agentDirectory.applyRuntimeBackoff(
                request.getAgentId(),
                backoffUntil,
                "Dispatch delivery failure " + safe(gatewayResult == null ? null : gatewayResult.gatewayStatus())
                        + " for dispatchRequestId=" + request.getDispatchRequestId()
                        + "; runtimeFailureCount=" + nextFailureCount
                        + (poisonAgent ? "; poison-agent-threshold-reached" : ""));
        return backoffUntil;
    }

    private boolean isRuntimeDeliveryFailure(GatewayDispatchResult result) {
        if (result == null || result.success()) {
            return false;
        }
        String status = safe(result.gatewayStatus()).toUpperCase(Locale.ROOT);
        if (status.contains("INVALID_COMMAND") || status.contains("INVALID_DISPATCH_REQUEST")) {
            return false;
        }
        return result.httpStatus() == 0
                || result.httpStatus() >= 500
                || status.contains("AGENT_NOT_CONNECTED")
                || status.contains("AGENT_NOT_AUTHORIZED")
                || status.contains("CONNECTION_NOT_WRITABLE")
                || status.contains("DELIVERY_TIMEOUT")
                || status.contains("DELIVERY_FAILED")
                || status.contains("GATEWAY_UNAVAILABLE")
                || status.contains("UNAVAILABLE")
                || status.contains("DISPATCH_EXCEPTION");
    }

    private boolean isTerminalTask(TaskStatus status) {
        return status != null && status.isTerminal();
    }

    private Duration computeRuntimeBackoff(int failureCount, String stableJitterKey) {
        long multiplier = 1L << Math.max(0, Math.min(failureCount - 1, 10));
        Duration initial = properties.getFailureRequeue().getRuntimeInitialBackoff();
        Duration max = properties.getFailureRequeue().getRuntimeMaxBackoff();
        Duration candidate = initial.multipliedBy(multiplier);
        Duration capped = candidate.compareTo(max) > 0 ? max : candidate;
        return applyDeterministicJitter(capped, stableJitterKey, properties.getFailureRequeue().getRuntimeJitterPercent());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void updateTaskDispatched(DispatchRequest request, OffsetDateTime now) {
        taskOrchestrationFacade.findTask(request.getTaskId()).ifPresent(task -> {
            task.setStatus(TaskStatus.DISPATCHED);
            task.setUpdatedAt(now);
            task.setLifecycleReason("Netty dispatch accepted");
            taskOrchestrationFacade.saveExecutionState(task);
        });
    }

    private void updateTaskRetryWaiting(DispatchRequest request, OffsetDateTime now) {
        taskOrchestrationFacade.findTask(request.getTaskId()).ifPresent(task -> {
            task.setStatus(TaskStatus.RETRY_WAIT);
            task.setNextDispatchAttemptAt(request.getNextRetryAt());
            task.setDispatchAttemptCount(request.getAttemptCount());
            task.setDispatchRetryReason("Netty dispatch retry scheduled: " + safe(request.getLastError()));
            task.setUpdatedAt(now);
            task.setLifecycleReason(task.getDispatchRetryReason());
            taskOrchestrationFacade.saveExecutionState(task);
        });
    }

    private void updateTaskDeadLetter(
            DispatchRequest request,
            OffsetDateTime now,
            String error) {
        taskOrchestrationFacade.findTask(request.getTaskId()).ifPresent(task -> {
            task.setStatus(TaskStatus.DEAD_LETTER);
            task.setTerminalAt(now);
            task.setNextDispatchAttemptAt(null);
            task.setDispatchRetryReason("Netty dispatch dead-letter: " + error);
            task.setUpdatedAt(now);
            task.setLifecycleReason(task.getDispatchRetryReason());
            taskOrchestrationFacade.saveExecutionState(task);
        });
    }

    private void publishDeadLetter(DispatchRequest request, OffsetDateTime now) {
        eventPublisher.publish(new DispatchDeadLetteredEvent(
                "dispatch-dead-letter-"
                        + request.getDispatchRequestId()
                        + "-"
                        + request.getAttemptCount(),
                request.getDispatchRequestId(),
                request.getAssignmentId(),
                request.getTaskId(),
                request.getIncidentId(),
                request.getAgentId(),
                request.getAttemptCount(),
                request.getReason(),
                now));
    }

    private DispatchExecutionResult record(DispatchExecutionResult result, long startedAt) {
        metrics.recordDispatchExecution(
                result,
                Duration.ofNanos(System.nanoTime() - startedAt));
        return result;
    }

    private void scheduleRetry(
            DispatchRequest request,
            OffsetDateTime now,
            String error) {
        Duration backoff = computeBackoff(request.getAttemptCount(), request.getDispatchRequestId());
        request.setStatus(DispatchRequestStatus.RETRY_WAITING);
        request.setRetryWaitingAt(now);
        request.setNextRetryAt(now.plus(backoff));
        request.setFailedAt(now);
        request.setUpdatedAt(now);
        request.setLastError(error);
        request.setReason("Netty dispatch failed; retry waiting for " + backoff + ": " + error);
    }

    private Duration computeBackoff(int attemptCount, String stableJitterKey) {
        long multiplier = 1L << Math.max(0, Math.min(attemptCount - 1, 10));
        Duration initial = properties.getRetry().getInitialBackoff();
        Duration max = properties.getRetry().getMaxBackoff();
        Duration candidate = initial.multipliedBy(multiplier);
        Duration capped = candidate.compareTo(max) > 0 ? max : candidate;
        return applyDeterministicJitter(capped, stableJitterKey, properties.getRetry().getJitterPercent());
    }

    private Duration applyDeterministicJitter(Duration base, String stableJitterKey, int jitterPercent) {
        if (base == null || base.isZero() || base.isNegative() || jitterPercent <= 0 || blank(stableJitterKey)) {
            return base == null ? Duration.ZERO : base;
        }
        long millis = Math.max(1L, base.toMillis());
        long spread = Math.max(1L, millis * Math.min(jitterPercent, 100) / 100L);
        int bucket = Math.floorMod(stableJitterKey.hashCode(), 201) - 100;
        long delta = spread * bucket / 100L;
        return Duration.ofMillis(Math.max(1L, millis + delta));
    }

    private void clearClaim(DispatchRequest request) {
        request.setClaimedBy(null);
        request.setClaimStartedAt(null);
        request.setClaimUntil(null);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
