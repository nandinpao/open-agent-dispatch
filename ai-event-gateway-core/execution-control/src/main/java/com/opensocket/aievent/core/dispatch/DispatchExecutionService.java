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
import com.opensocket.aievent.core.events.A2ADispatchProgressedEvent;
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

    /** Stage 5 additive pre-network safety guards. Empty preserves legacy behavior. */
    @Autowired(required = false)
    private List<DispatchExecutionSafetyGuard> executionSafetyGuards = List.of();
    /** Final Core-owned admission gates. They commit authority before network and never perform network I/O. */
    @Autowired(required = false)
    private List<DispatchPreSendAdmission> preSendAdmissions = List.of();
    /** A0-R7 additive lifecycle observers. They never initiate dispatch themselves. */
    @Autowired(required = false)
    private List<DispatchExecutionLifecycleObserver> executionLifecycleObservers = List.of();
    @Autowired(required = false)
    private DispatchAssignmentEvidenceService assignmentEvidenceService;

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
        if (request.getCommand() != null) { request.getCommand().setAttemptNo(request.getAttemptCount()); }
        OffsetDateTime claimHeartbeatAt = OffsetDateTime.now(ZoneOffset.UTC);
        DispatchExecutionSafetyDecision safetyDecision = evaluateExecutionSafety(request, claimHeartbeatAt);
        if (!safetyDecision.allowed()) {
            return handleSafetyDecision(request, ownership, safetyDecision, claimHeartbeatAt, startedAt);
        }
        PersistenceWriteResult dispatchingWrite = dispatchRepository.markClaimDispatching(request.getDispatchRequestId(), ownership, claimHeartbeatAt);
        if (!dispatchingWrite.applied()) {
            return record(claimLostResult(request, null, dispatchingWrite), startedAt);
        }
        request.setOutboxStatus(DispatchOutboxStatus.DISPATCHING);
        request.setClaimHeartbeatAt(claimHeartbeatAt);
        recordAssignmentEvidence(request, "CLAIMED", null, null, "{\"authority\":\"DISPATCH_AUTHORITY\"}");
        publishA2AProgress(request, "CLAIMED", null, "Dispatch worker claimed durable intent", "dispatch-claim:" + request.getDispatchRequestId() + ":" + request.getAttemptCount());
        recordAttemptStarted(request);
        log.info("dispatch_delivery_attempt_started dispatchRequestId={} taskId={} assignmentId={} agentId={} attemptCount={} gatewayNode={} gatewayPath={} clientEnabled={} gatewayBaseUrl={}",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAssignmentId()), safe(request.getAgentId()),
                request.getAttemptCount(), safe(request.getOwnerGatewayNodeId()), safe(request.getGatewayDispatchPath()), properties.getClient().isEnabled(),
                safe(properties.getClient().getDefaultGatewayBaseUrl()));

        GatewayDispatchResult gatewayResult;
        try {
            // All non-authoritative observer preparation happens before the final authority gate.
            // After the admission transaction commits, no database write or observer callback may
            // occur before the network call. This keeps the revalidation -> SEND_STARTED -> send
            // boundary as narrow as possible without holding a database transaction across I/O.
            notifyBeforeNetwork(request, OffsetDateTime.now(ZoneOffset.UTC));
            DispatchPreSendAdmissionDecision admission = admitPreSend(request, OffsetDateTime.now(ZoneOffset.UTC));
            if (!admission.allowed()) {
                return handleSafetyDecision(
                        request,
                        ownership,
                        new DispatchExecutionSafetyDecision(false, admission.disposition(), admission.reasonCode(), admission.message()),
                        OffsetDateTime.now(ZoneOffset.UTC),
                        startedAt);
            }
            DispatchSendPermit permit = admission.permit();
            log.info("dispatch_pre_send_admitted dispatchRequestId={} assignmentId={} permitId={} authorityVersion={} leaseId={} fencingToken={} expiresAt={}",
                    safe(request.getDispatchRequestId()), safe(request.getAssignmentId()), safe(permit == null ? null : permit.permitId()),
                    safe(permit == null ? null : permit.authorityVersion()), safe(permit == null ? null : permit.leaseId()),
                    permit == null ? null : permit.fencingToken(), permit == null ? null : permit.expiresAt());
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
        notifyAfterNetwork(request, gatewayResult, completedAt);
        log.info("dispatch_delivery_attempt_result dispatchRequestId={} taskId={} agentId={} success={} gatewayStatus={} message={}",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAgentId()), gatewayResult.success(),
                safe(gatewayResult.gatewayStatus()), safe(gatewayResult.message()));
        if (gatewayResult.success()) {
            request.setStatus(DispatchRequestStatus.DISPATCHED);
            request.setOutboxStatus(DispatchOutboxStatus.ACKNOWLEDGED);
            request.setRecoveryClassification(DispatchRecoveryClassification.NONE);
            request.setUncertainSince(null);
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
            recordAssignmentEvidence(request, "GATEWAY_ACCEPTED", gatewayResult.gatewayStatus(), null,
                    "{\"delivery\":\"accepted\"}");
            clearClaim(request);
            clearRuntimeBackoffAfterSuccessfulDispatch(request);
            updateTaskDispatched(request, completedAt);
            recordGatewayDelivered(request, gatewayResult, completedAt);
            publishA2AProgress(request, "ACKNOWLEDGED", null, request.getReason(), "gateway-accepted:" + request.getDispatchRequestId() + ":" + request.getAttemptCount());

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
        // PC-S2: once SEND_STARTED has committed, a timeout/connection exception is an unknown
        // delivery outcome, not proof of non-delivery. Blind retry or reassignment can duplicate
        // side effects at the executor. Hold for callback/reconciliation instead.
        if (isUncertainGatewayResult(gatewayResult)) {
            return holdDeliveryUnknown(request, ownership, gatewayResult, completedAt, error, startedAt);
        }
        if (shouldRequeueAfterRuntimeFailure(request, gatewayResult)) {
            return requeueAfterRuntimeFailure(request, ownership, gatewayResult, completedAt, error, startedAt);
        }
        if (properties.getRetry().isEnabled()
                && request.getAttemptCount() < properties.getRetry().getMaxAttempts()) {
            scheduleRetry(request, completedAt, error);
            request.setOutboxStatus(DispatchOutboxStatus.FAILED_RETRYABLE);
            recordRetryWaiting(request, gatewayResult, completedAt);
            recordAssignmentEvidence(request, "FAILED_RETRYABLE", gatewayResult.gatewayStatus(), null,
                    "{\"retryAt\":\"" + request.getNextRetryAt() + "\"}");
            PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
            if (!write.applied()) {
                log.warn("dispatch_delivery_claim_lost dispatchRequestId={} taskId={} agentId={} gatewayStatus={} writeResult={}",
                        safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAgentId()), safe(gatewayResult.gatewayStatus()), write);
                return record(claimLostResult(request, gatewayResult, write), startedAt);
            }
            publishA2AProgress(request, "FAILED_RETRYABLE", "DISPATCH_RETRY_WAITING", request.getReason(),
                    "dispatch-retry:" + request.getDispatchRequestId() + ":" + request.getAttemptCount());
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
        request.setOutboxStatus(DispatchOutboxStatus.DEAD_LETTER);
        request.setRecoveryClassification(DispatchRecoveryClassification.RETRY_EXHAUSTED);
        request.setFailedAt(completedAt);
        request.setDeadLetterAt(completedAt);
        request.setUpdatedAt(completedAt);
        request.setLastError(error);
        request.setReason("Netty dispatch failed and max attempts reached: " + error);
        PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
        if (!write.applied()) {
            return record(claimLostResult(request, gatewayResult, write), startedAt);
        }
        recordAssignmentEvidence(request, "DEAD_LETTER", gatewayResult.gatewayStatus(), null,
                "{\"retryExhausted\":true}");
        clearClaim(request);
        publishDeadLetter(request, completedAt);
        updateTaskDeadLetter(request, completedAt, error);
        recordDeadLettered(request, gatewayResult, completedAt);
        publishA2AProgress(request, "DEAD_LETTER", "DISPATCH_DEAD_LETTER", request.getReason(), "dispatch-dead-letter:" + request.getDispatchRequestId());

        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setGatewayStatus(gatewayResult.gatewayStatus());
        result.setTaskStatus(TaskStatus.DEAD_LETTER.name());
        result.setMessage(gatewayResult.message());
        return record(result, startedAt);
    }

    private DispatchPreSendAdmissionDecision admitPreSend(DispatchRequest request, OffsetDateTime now) {
        if (preSendAdmissions == null || preSendAdmissions.isEmpty()) {
            return DispatchPreSendAdmissionDecision.allow(
                    "LEGACY_PRE_SEND_ADMISSION",
                    "No additive pre-send authority gate configured",
                    DispatchSendPermit.legacy(request, now));
        }
        DispatchSendPermit permit = null;
        boolean applicable = false;
        for (DispatchPreSendAdmission admission : preSendAdmissions.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(DispatchPreSendAdmission::order))
                .toList()) {
            DispatchPreSendAdmissionDecision decision;
            try {
                decision = admission.admit(request, now);
            } catch (RuntimeException ex) {
                log.error("dispatch_pre_send_admission_failed dispatchRequestId={} admission={} authoritativeStateUnchanged=true",
                        safe(request == null ? null : request.getDispatchRequestId()), admission.getClass().getName(), ex);
                return DispatchPreSendAdmissionDecision.retryInfrastructure(
                        "PRE_SEND_ADMISSION_FAILED",
                        "Final pre-send authority admission failed closed");
            }
            if (decision == null) {
                return DispatchPreSendAdmissionDecision.retryInfrastructure(
                        "PRE_SEND_ADMISSION_EMPTY_DECISION",
                        "Final pre-send authority admission returned no decision");
            }
            if (!decision.applicable()) continue;
            applicable = true;
            if (!decision.allowed()) return decision;
            if (decision.permit() != null) {
                if (permit != null) {
                    return DispatchPreSendAdmissionDecision.terminal(
                            "MULTIPLE_PRE_SEND_AUTHORITIES",
                            "More than one pre-send authority attempted to issue a send permit");
                }
                permit = decision.permit();
            }
        }
        if (!applicable) {
            return DispatchPreSendAdmissionDecision.allow(
                    "LEGACY_PRE_SEND_ADMISSION",
                    "No current-authority pre-send admission applied",
                    DispatchSendPermit.legacy(request, now));
        }
        if (permit == null) {
            return DispatchPreSendAdmissionDecision.retryInfrastructure(
                    "PRE_SEND_PERMIT_MISSING",
                    "Applicable pre-send authority did not issue a send permit");
        }
        return DispatchPreSendAdmissionDecision.allow(
                "PRE_SEND_ADMITTED",
                "Final pre-send authority admission passed",
                permit);
    }

    private void notifyBeforeNetwork(DispatchRequest request, OffsetDateTime at) {
        executionLifecycleObservers.stream().sorted(java.util.Comparator.comparingInt(DispatchExecutionLifecycleObserver::order))
                .forEach(observer -> observer.beforeNetwork(request, at));
    }

    private void notifyAfterNetwork(DispatchRequest request, GatewayDispatchResult result, OffsetDateTime at) {
        for (DispatchExecutionLifecycleObserver observer : executionLifecycleObservers.stream().sorted(java.util.Comparator.comparingInt(DispatchExecutionLifecycleObserver::order)).toList()) {
            try { observer.afterNetwork(request, result, at); }
            catch (RuntimeException ex) { log.error("dispatch_lifecycle_observer_failed dispatchRequestId={} observer={} error={}", safe(request == null ? null : request.getDispatchRequestId()), observer.getClass().getSimpleName(), rootMessage(ex)); }
        }
    }

    private DispatchExecutionSafetyDecision evaluateExecutionSafety(DispatchRequest request, OffsetDateTime now) {
        if (executionSafetyGuards == null || executionSafetyGuards.isEmpty()) {
            return DispatchExecutionSafetyDecision.allow("No additive execution safety guard configured");
        }
        return executionSafetyGuards.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(DispatchExecutionSafetyGuard::order))
                .map(guard -> {
                    try {
                        DispatchExecutionSafetyDecision decision = guard.evaluate(request, now);
                        return decision == null
                                ? DispatchExecutionSafetyDecision.retryInfrastructure("EXECUTION_SAFETY_EMPTY_DECISION", "Execution safety guard returned no decision")
                                : decision;
                    } catch (RuntimeException ex) {
                        log.error("dispatch_execution_safety_guard_failed dispatchRequestId={} guard={} authoritativeStateUnchanged=true",
                                safe(request.getDispatchRequestId()), guard.getClass().getName(), ex);
                        return DispatchExecutionSafetyDecision.retryInfrastructure("EXECUTION_SAFETY_GUARD_FAILED", "Execution safety guard failed closed");
                    }
                })
                .filter(decision -> !decision.allowed())
                .findFirst()
                .orElseGet(() -> DispatchExecutionSafetyDecision.allow("All execution safety guards passed"));
    }

    private DispatchExecutionResult handleSafetyDecision(DispatchRequest request, ClaimOwnership ownership,
            DispatchExecutionSafetyDecision decision, OffsetDateTime now, long startedAt) {
        DispatchFailureDisposition disposition = decision.disposition() == null
                ? DispatchFailureDisposition.TERMINAL_FAILURE
                : decision.disposition();
        return switch (disposition) {
            case RETRY_INFRASTRUCTURE -> retryInfrastructureFailure(request, ownership, decision, now, startedAt);
            case REASSIGN_REQUIRED -> reassignAfterAuthorityLoss(request, ownership, decision, now, startedAt);
            case BLOCK_POLICY -> blockDispatch(request, ownership, decision, now, startedAt, false);
            case BLOCK_SECURITY -> blockDispatch(request, ownership, decision, now, startedAt, true);
            case TERMINAL_FAILURE -> terminalSafetyFailure(request, ownership, decision, now, startedAt);
            case NONE -> throw new IllegalStateException("Blocked dispatch cannot use NONE failure disposition");
        };
    }

    private DispatchExecutionResult retryInfrastructureFailure(DispatchRequest request, ClaimOwnership ownership,
            DispatchExecutionSafetyDecision decision, OffsetDateTime now, long startedAt) {
        String reason = failureReason(decision);
        if (properties.getRetry().isEnabled() && request.getAttemptCount() < properties.getRetry().getMaxAttempts()) {
            scheduleRetry(request, now, reason);
            request.setOutboxStatus(DispatchOutboxStatus.FAILED_RETRYABLE);
            request.setRecoveryClassification(DispatchRecoveryClassification.INFRASTRUCTURE_RETRY);
            request.setLastError(reason);
            request.setReason("Infrastructure safety failure; retry scheduled: " + reason);
            PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
            if (!write.applied()) return record(claimLostResult(request, null, write), startedAt);
            recordAssignmentEvidence(request, "SAFETY_RETRY_INFRASTRUCTURE", null, null,
                    failureEvidence(decision));
            clearClaim(request);
            updateTaskRetryWaiting(request, now);
            return safetyResult(request, TaskStatus.RETRY_WAIT, reason, startedAt);
        }
        return terminalSafetyFailure(
                request, ownership,
                DispatchExecutionSafetyDecision.terminal("INFRASTRUCTURE_RETRY_EXHAUSTED", reason),
                now, startedAt);
    }

    private DispatchExecutionResult reassignAfterAuthorityLoss(DispatchRequest request, ClaimOwnership ownership,
            DispatchExecutionSafetyDecision decision, OffsetDateTime now, long startedAt) {
        String reason = failureReason(decision);
        request.setStatus(DispatchRequestStatus.FAILED);
        request.setOutboxStatus(DispatchOutboxStatus.ABANDONED);
        request.setRecoveryClassification(DispatchRecoveryClassification.REASSIGN_REQUIRED);
        request.setFailedAt(now);
        request.setUpdatedAt(now);
        request.setLastError(reason);
        request.setReason("Stale dispatch authority abandoned; Core must select a new assignment: " + reason);
        PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
        if (!write.applied()) return record(claimLostResult(request, null, write), startedAt);
        recordAssignmentEvidence(request, "SAFETY_REASSIGN_REQUIRED", null, null, failureEvidence(decision));
        clearClaim(request);
        TaskRecord task = taskOrchestrationFacade.reassignTask(
                request.getTaskId(),
                "Dispatch authority lost; previousAssignment=" + request.getAssignmentId() + "; reason=" + reason,
                now);
        recordTaskRequeued(request, task, request.getReason(), now);
        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setTaskStatus(task == null || task.getStatus() == null ? TaskStatus.QUEUED.name() : task.getStatus().name());
        result.setMessage(reason);
        log.warn("dispatch_execution_reassignment_required dispatchRequestId={} taskId={} assignmentId={} reasonCode={}",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAssignmentId()), safe(decision.reasonCode()));
        return record(result, startedAt);
    }

    private DispatchExecutionResult blockDispatch(DispatchRequest request, ClaimOwnership ownership,
            DispatchExecutionSafetyDecision decision, OffsetDateTime now, long startedAt, boolean security) {
        String reason = failureReason(decision);
        request.setStatus(DispatchRequestStatus.FAILED);
        request.setOutboxStatus(DispatchOutboxStatus.BLOCKED);
        request.setRecoveryClassification(security
                ? DispatchRecoveryClassification.SECURITY_BLOCKED
                : DispatchRecoveryClassification.POLICY_BLOCKED);
        request.setFailedAt(now);
        request.setUpdatedAt(now);
        request.setLastError(reason);
        request.setReason((security ? "Security" : "Policy") + " authority blocked dispatch: " + reason);
        PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
        if (!write.applied()) return record(claimLostResult(request, null, write), startedAt);
        recordAssignmentEvidence(request, security ? "SAFETY_SECURITY_BLOCKED" : "SAFETY_POLICY_BLOCKED",
                null, null, failureEvidence(decision));
        clearClaim(request);
        updateTaskBlocked(request, now, reason, security);
        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setTaskStatus(TaskStatus.BLOCKED.name());
        result.setMessage(reason);
        log.warn("dispatch_execution_{}_blocked dispatchRequestId={} taskId={} assignmentId={} reasonCode={}",
                security ? "security" : "policy", safe(request.getDispatchRequestId()), safe(request.getTaskId()),
                safe(request.getAssignmentId()), safe(decision.reasonCode()));
        return record(result, startedAt);
    }

    private DispatchExecutionResult terminalSafetyFailure(DispatchRequest request, ClaimOwnership ownership,
            DispatchExecutionSafetyDecision decision, OffsetDateTime now, long startedAt) {
        String reason = failureReason(decision);
        request.setStatus(DispatchRequestStatus.DEAD_LETTER);
        request.setOutboxStatus(DispatchOutboxStatus.DEAD_LETTER);
        request.setRecoveryClassification(DispatchRecoveryClassification.TERMINAL_FAILURE);
        request.setFailedAt(now);
        request.setDeadLetterAt(now);
        request.setUpdatedAt(now);
        request.setLastError(reason);
        request.setReason(reason);
        PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
        if (!write.applied()) return record(claimLostResult(request, null, write), startedAt);
        recordAssignmentEvidence(request, "EXECUTION_SAFETY_TERMINAL", null, null, failureEvidence(decision));
        clearClaim(request);
        publishDeadLetter(request, now);
        updateTaskDeadLetter(request, now, reason);
        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setTaskStatus(TaskStatus.DEAD_LETTER.name());
        result.setMessage(reason);
        log.warn("dispatch_execution_terminal_failure dispatchRequestId={} taskId={} assignmentId={} reasonCode={}",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAssignmentId()), safe(decision.reasonCode()));
        return record(result, startedAt);
    }

    private DispatchExecutionResult safetyResult(DispatchRequest request, TaskStatus taskStatus, String message, long startedAt) {
        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setTaskStatus(taskStatus.name());
        result.setMessage(message);
        return record(result, startedAt);
    }

    private String failureReason(DispatchExecutionSafetyDecision decision) {
        return "SAFETY_" + decision.disposition().name() + ":" + safe(decision.reasonCode()) + ":" + safe(decision.message());
    }

    private String failureEvidence(DispatchExecutionSafetyDecision decision) {
        return "{\"disposition\":\"" + safe(decision.disposition() == null ? null : decision.disposition().name())
                + "\",\"reasonCode\":\"" + safe(decision.reasonCode()) + "\"}";
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
            OffsetDateTime uncertainAt = OffsetDateTime.now(ZoneOffset.UTC);
            attempted.setRecoveryClassification(DispatchRecoveryClassification.ACK_PERSISTENCE_UNCERTAIN);
            attempted.setUncertainSince(uncertainAt);
            persistUncertainGatewayAcceptance(attempted, uncertainAt, write);
            recordGatewayDeliveredUnconfirmed(attempted, gatewayResult, write, uncertainAt);
            recordAssignmentEvidence(attempted, "GATEWAY_ACCEPTED_UNCONFIRMED", gatewayResult.gatewayStatus(), null, "{\"writeOutcome\":\"" + write.outcome() + "\"}");
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

    private void persistUncertainGatewayAcceptance(
            DispatchRequest attempted,
            OffsetDateTime uncertainAt,
            PersistenceWriteResult write) {
        DispatchStatusTransition uncertain = new DispatchStatusTransition();
        uncertain.setDispatchRequestId(attempted.getDispatchRequestId());
        uncertain.setAllowedCurrentStatuses(List.of(DispatchRequestStatus.DISPATCHING));
        uncertain.setNewStatus(DispatchRequestStatus.DISPATCHING);
        uncertain.setExpectedAttemptNo(attempted.getAttemptCount());
        uncertain.setExpectedDispatchToken(attempted.getDispatchToken());
        uncertain.setOutboxStatus(DispatchOutboxStatus.DISPATCHING);
        uncertain.setRecoveryClassification(DispatchRecoveryClassification.ACK_PERSISTENCE_UNCERTAIN);
        uncertain.setUncertainSince(uncertainAt);
        uncertain.setReason("Gateway accepted but local final persistence lost claim ownership: " + write.outcome());
        uncertain.setUpdatedAt(uncertainAt);
        uncertain.setClearClaim(false);
        dispatchRepository.transitionStatus(uncertain);
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

    private DispatchExecutionResult holdDeliveryUnknown(
            DispatchRequest request,
            ClaimOwnership ownership,
            GatewayDispatchResult gatewayResult,
            OffsetDateTime now,
            String error,
            long startedAt) {
        request.setStatus(DispatchRequestStatus.DELIVERY_UNKNOWN);
        request.setOutboxStatus(DispatchOutboxStatus.RECOVERY_PENDING);
        request.setRecoveryClassification(DispatchRecoveryClassification.RESPONSE_LOST);
        request.setUncertainSince(now);
        request.setNextRetryAt(null);
        request.setRetryWaitingAt(null);
        request.setUpdatedAt(now);
        request.setLastError(error);
        request.setReason("Dispatch delivery outcome is unknown; automatic retry/reassignment is suppressed pending callback or reconciliation: " + error);
        PersistenceWriteResult write = dispatchRepository.saveClaimed(request, ownership);
        if (!write.applied()) {
            return record(claimLostResult(request, gatewayResult, write), startedAt);
        }
        recordAssignmentEvidence(request, "DELIVERY_UNKNOWN", gatewayResult.gatewayStatus(), null,
                "{\"automaticRetrySuppressed\":true}");
        clearClaim(request);
        updateTaskReconciling(request, now, error);
        publishA2AProgress(request, "DELIVERY_UNKNOWN", "DISPATCH_RESPONSE_LOST", request.getReason(),
                "dispatch-delivery-unknown:" + request.getDispatchRequestId() + ":" + request.getAttemptCount());

        DispatchExecutionResult result = DispatchExecutionResult.from(request);
        result.setExecuted(false);
        result.setGatewayStatus(gatewayResult.gatewayStatus());
        result.setTaskStatus(TaskStatus.RECONCILING.name());
        result.setMessage(request.getReason());
        log.warn("dispatch_delivery_unknown dispatchRequestId={} taskId={} assignmentId={} attemptCount={} automaticRetrySuppressed=true",
                safe(request.getDispatchRequestId()), safe(request.getTaskId()), safe(request.getAssignmentId()), request.getAttemptCount());
        return record(result, startedAt);
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
        // Runtime delivery failure is not a retry of this same dispatch authority.
        // Core is about to cancel the stale assignment and select a replacement, so the
        // current durable outbox row must leave CLAIMED/DISPATCHING before saveClaimed()
        // clears its claim evidence. Otherwise ck_dispatch_claim_evidence_p2d correctly
        // rejects a DISPATCHING row whose claimed_by/claim_token/claim_until were nulled.
        request.setOutboxStatus(DispatchOutboxStatus.ABANDONED);
        request.setRecoveryClassification(DispatchRecoveryClassification.REASSIGN_REQUIRED);
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

    private void updateTaskReconciling(DispatchRequest request, OffsetDateTime now, String reason) {
        taskOrchestrationFacade.findTask(request.getTaskId()).ifPresent(task -> {
            task.setStatus(TaskStatus.RECONCILING);
            task.setNextDispatchAttemptAt(null);
            task.setDispatchRetryReason("Dispatch delivery outcome unknown; reconciliation required: " + safe(reason));
            task.setUpdatedAt(now);
            task.setLifecycleReason(task.getDispatchRetryReason());
            taskOrchestrationFacade.saveExecutionState(task);
        });
    }

    private void updateTaskBlocked(DispatchRequest request, OffsetDateTime now, String reason, boolean security) {
        taskOrchestrationFacade.findTask(request.getTaskId()).ifPresent(task -> {
            task.setStatus(TaskStatus.BLOCKED);
            task.setNextDispatchAttemptAt(null);
            task.setDispatchRetryReason((security ? "Security" : "Policy") + " dispatch block: " + reason);
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
                request.getTenantId(),
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
        request.setOutboxStatus(DispatchOutboxStatus.FAILED_RETRYABLE);
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
        request.setClaimToken(null);
        request.setClaimHeartbeatAt(null);
    }


    private boolean isUncertainGatewayResult(GatewayDispatchResult result) {
        if (result == null) return true;
        String status = safe(result.gatewayStatus()).toUpperCase(Locale.ROOT);
        return result.httpStatus() == 0 || status.contains("TIMEOUT") || status.contains("NULL_GATEWAY_RESPONSE") || status.contains("DISPATCH_EXCEPTION");
    }

    private void recordAssignmentEvidence(DispatchRequest request, String eventType, String gatewayStatus, String ackEvidenceId, String json) {
        if (assignmentEvidenceService != null) assignmentEvidenceService.record(request, eventType, gatewayStatus, ackEvidenceId, json);
    }

    private void publishA2AProgress(DispatchRequest request, String stage, String blockerCode, String reason, String evidenceReference) {
        if (request == null || request.getTenantId() == null || request.getTaskId() == null) return;
        eventPublisher.publish(new A2ADispatchProgressedEvent("a2a-dispatch-progress-" + java.util.UUID.randomUUID(),
                request.getTenantId(), request.getTaskId(), request.getDispatchRequestId(), stage, blockerCode, reason,
                evidenceReference, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
