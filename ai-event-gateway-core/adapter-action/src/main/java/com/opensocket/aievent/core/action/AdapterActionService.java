package com.opensocket.aievent.core.action;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.events.AdapterActionRequestedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.LeaseRenewalRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteVerifier;

@Service
public class AdapterActionService implements AdapterActionFacade {
    private static final Logger log = LoggerFactory.getLogger(AdapterActionService.class);

    private final AdapterActionRepository repository;
    private final IncidentFacade incidentFacade;
    private final AdapterActionProperties properties;

    @Autowired(required = false)
    private AdapterActionMetricsPort metrics = AdapterActionMetricsPort.noop();

    @Autowired(required = false)
    private ModuleEventPublisher eventPublisher = ModuleEventPublisher.noop();

    @Autowired
    private AdapterExecutorAuditRepository auditRepository;

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

    public AdapterActionService(AdapterActionRepository repository,
                                IncidentFacade incidentFacade,
                                AdapterActionProperties properties) {
        this.repository = repository;
        this.incidentFacade = incidentFacade;
        this.properties = properties;
    }

    public void onTerminalTaskCallback(TaskRecord task,
                                       DispatchRequest dispatchRequest,
                                       TaskCallbackRequest callback,
                                       TaskCallbackType callbackType) {
        evaluateAfterTaskCallback(task, dispatchRequest, callback, callbackType);
    }

    @Transactional
    public AdapterActionOrchestrationResult evaluateAfterTaskCallback(TaskRecord task,
                                                                       DispatchRequest dispatchRequest,
                                                                       TaskCallbackRequest callback,
                                                                       TaskCallbackType callbackType) {
        AdapterActionOrchestrationResult result = new AdapterActionOrchestrationResult();
        result.setTaskId(task == null ? null : task.getTaskId());
        result.setIncidentId(task == null ? null : task.getIncidentId());
        log.info("issue_sync_evaluation_started taskId={} incidentId={} taskStatus={} callbackType={} callbackId={} dispatchRequestId={} issueEnabled={} mcpEnabled={}",
                task == null ? null : task.getTaskId(), task == null ? null : task.getIncidentId(), task == null ? null : task.getStatus(),
                callbackType, callback == null ? null : callback.getCallbackId(), dispatchRequest == null ? null : dispatchRequest.getDispatchRequestId(),
                properties.getIssue().isEnabled(), properties.getMcp().isEnabled());
        if (task == null || !isTerminal(task.getStatus())) {
            log.info("issue_sync_evaluation_skipped taskId={} reason={}", task == null ? null : task.getTaskId(), task == null ? "TASK_MISSING" : "TASK_NOT_TERMINAL");
            result.setTerminalTask(false);
            result.setActions(List.of());
            return result;
        }
        result.setTerminalTask(true);

        Incident incident = task.getIncidentId() == null ? null : incidentFacade.findById(task.getIncidentId()).orElse(null);
        log.info("issue_sync_incident_resolved taskId={} incidentId={} incidentFound={} linkedIssueId={}",
                task.getTaskId(), task.getIncidentId(), incident != null, incident == null ? null : incident.getLinkedIssueId());
        boolean completed = task.getStatus() != null && task.getStatus().isSucceeded();
        boolean failed = task.getStatus() != null && task.getStatus().isFailed();

        List<AdapterAction> actions = new ArrayList<>();
        if (shouldEvaluateMcp(completed, failed)) {
            AdapterAction action = createOrSuppress(
                    AdapterType.MCP,
                    AdapterActionType.MCP_CONTEXT_FETCH,
                    properties.getMcp().getAdapterName(),
                    mcpIdempotencyKey(task),
                    task,
                    dispatchRequest,
                    incident,
                    properties.getMcp().isEnabled(),
                    "MCP context fetch requested after task terminal status " + task.getStatus(),
                    "MCP action disabled or already exists for this task",
                    callback,
                    callbackType);
            if (action != null) {
                actions.add(action);
                result.setMcpActionCreated(action.getStatus() == AdapterActionStatus.PENDING);
            }
        }

        if (shouldEvaluateIssue(completed, failed)) {
            boolean hasLinkedIssue = incident != null && incident.getLinkedIssueId() != null && !incident.getLinkedIssueId().isBlank();
            AdapterActionType actionType = hasLinkedIssue && properties.getIssue().isUpdateExistingIssueComment()
                    ? AdapterActionType.ISSUE_UPDATE_COMMENT
                    : AdapterActionType.ISSUE_CREATE;
            String idemKey = actionType == AdapterActionType.ISSUE_CREATE
                    ? issueCreateIdempotencyKey(task, incident)
                    : issueUpdateIdempotencyKey(task, incident);
            String reason = actionType == AdapterActionType.ISSUE_CREATE
                    ? "Issue create requested after task terminal status " + task.getStatus()
                    : "Issue comment update requested for linked issue " + incident.getLinkedIssueId();
            AdapterAction action = createOrSuppress(
                    AdapterType.ISSUE_TRACKING,
                    actionType,
                    properties.getIssue().getAdapterName(),
                    idemKey,
                    task,
                    dispatchRequest,
                    incident,
                    properties.getIssue().isEnabled(),
                    reason,
                    "Issue action disabled or idempotency suppressed duplicate action",
                    callback,
                    callbackType);
            if (action != null) {
                log.info("issue_sync_action_orchestrated taskId={} incidentId={} actionId={} actionType={} adapterName={} status={} enabled={}",
                        task.getTaskId(), task.getIncidentId(), action.getActionId(), action.getActionType(), action.getAdapterName(), action.getStatus(), properties.getIssue().isEnabled());
                actions.add(action);
                result.setIssueActionCreated(action.getStatus() == AdapterActionStatus.PENDING);
            }
        }

        result.setCreatedCount((int) actions.stream().filter(a -> a.getStatus() == AdapterActionStatus.PENDING).count());
        result.setSuppressedCount((int) actions.stream().filter(a -> a.getStatus() == AdapterActionStatus.SUPPRESSED).count());
        log.info("issue_sync_evaluation_completed taskId={} incidentId={} createdCount={} suppressedCount={} actions={}",
                result.getTaskId(), result.getIncidentId(), result.getCreatedCount(), result.getSuppressedCount(), actions.size());
        actions.stream().filter(a -> a.getStatus() == AdapterActionStatus.PENDING).forEach(this::publishRequestedEvent);
        result.setActions(actions);
        return result;
    }

    private void publishRequestedEvent(AdapterAction action) {
        log.info("adapter_action_requested_event_published actionId={} taskId={} incidentId={} adapterType={} actionType={} status={} idempotencyKey={}",
                action.getActionId(), action.getTaskId(), action.getIncidentId(), action.getAdapterType(), action.getActionType(), action.getStatus(), action.getIdempotencyKey());
        eventPublisher.publish(new AdapterActionRequestedEvent(
                "evt-" + UUID.randomUUID(),
                action.getActionId(),
                action.getTaskId(),
                action.getIncidentId(),
                action.getAdapterType() == null ? null : action.getAdapterType().name(),
                action.getActionType() == null ? null : action.getActionType().name(),
                action.getStatus() == null ? null : action.getStatus().name(),
                action.getIdempotencyKey(),
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    public AdapterAction markCompleted(String actionId, String responseRef) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        action.setStatus(AdapterActionStatus.COMPLETED);
        action.setResponseRef(responseRef);
        action.setCompletedAt(now);
        action.setUpdatedAt(now);
        clearClaim(action);
        return saveAndRecord(action, "mark_completed");
    }

    public AdapterAction markFailed(String actionId, String error) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        action.setStatus(AdapterActionStatus.FAILED);
        action.setLastError(error);
        action.setFailedAt(now);
        action.setUpdatedAt(now);
        clearClaim(action);
        return saveAndRecord(action, "mark_failed");
    }

    public Optional<AdapterAction> claimNext(AdapterType adapterType, String workerId, Duration leaseDuration) {
        if (adapterType == null) {
            throw new IllegalArgumentException("adapterType is required");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClaimRequest claimRequest = ClaimRequest.forLease(
                requireWorkerId(workerId),
                now,
                effectiveLease(leaseDuration),
                1);
        Optional<AdapterAction> claimed = repository.claimNext(adapterType, claimRequest);
        claimed.ifPresent(action -> record(action, "worker_claim"));
        return claimed;
    }

    public AdapterAction heartbeat(String actionId, String workerId, Duration leaseDuration) {
        AdapterAction current = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        ensureClaimedBy(current, workerId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LeaseRenewalRequest request = new LeaseRenewalRequest(
                actionId,
                ownership(current),
                now,
                now.plus(effectiveLease(leaseDuration)));
        AdapterAction action = repository.extendLease(request)
                .orElseThrow(() -> new IllegalStateException(
                        "Adapter action lease ownership was lost for worker " + request.ownership().workerId() + ": " + actionId));
        record(action, "worker_heartbeat");
        return action;
    }

    public AdapterAction completeByWorker(String actionId, String workerId, String responseRef) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        ensureClaimedBy(action, workerId);
        ClaimOwnership ownership = ownership(action);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        action.setStatus(AdapterActionStatus.COMPLETED);
        action.setResponseRef(responseRef);
        action.setCompletedAt(now);
        action.setFailedAt(null);
        action.setNextAttemptAt(null);
        action.setUpdatedAt(now);
        PersistenceWriteVerifier.requireApplied(
                repository.saveClaimed(action, ownership, now),
                "complete adapter action by worker");
        clearClaim(action);
        record(action, "worker_complete");
        return action;
    }

    public AdapterAction failByWorker(String actionId, String workerId, String error) {
        return failByWorker(actionId, workerId, error, false);
    }

    public AdapterAction failByWorker(String actionId, String workerId, String error, Boolean retryable) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        ensureClaimedBy(action, workerId);
        ClaimOwnership ownership = ownership(action);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        action.setAttemptCount(action.getAttemptCount() + 1);
        action.setMaxAttempts(effectiveMaxAttempts(action));
        action.setLastError(error == null || error.isBlank() ? "Worker reported failure" : error);
        action.setFailedAt(now);
        action.setUpdatedAt(now);
        applyWorkerFailureStatus(action, Boolean.TRUE.equals(retryable), now, "Worker failure");
        PersistenceWriteVerifier.requireApplied(
                repository.saveClaimed(action, ownership, now),
                "fail adapter action by worker");
        clearClaim(action);
        record(action, "worker_fail");
        return action;
    }

    public List<AdapterAction> recoverExpiredWorkerLeases(int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int capped = Math.max(1, Math.min(limit, properties.getWorker().getExpiredLeaseScanBatchSize()));
        return repository.findByStatus(AdapterActionStatus.CLAIMED, capped).stream()
                .filter(action -> action.getLeaseExpiresAt() != null && !action.getLeaseExpiresAt().isAfter(now))
                .map(action -> recoverExpiredWorkerLease(action, now))
                .toList();
    }

    public AdapterAction recoverExpiredWorkerLease(String actionId) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (action.getStatus() != AdapterActionStatus.CLAIMED) {
            throw new IllegalStateException("Only CLAIMED adapter action can be lease-recovered: " + actionId + " status=" + action.getStatus());
        }
        if (action.getLeaseExpiresAt() != null && action.getLeaseExpiresAt().isAfter(now)) {
            throw new IllegalStateException("Adapter action lease has not expired yet: " + actionId + " leaseExpiresAt=" + action.getLeaseExpiresAt());
        }
        return recoverExpiredWorkerLease(action, now);
    }

    public AdapterAction retryForWorker(String actionId, String reason, boolean resetAttempts) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        if (action.getStatus() != AdapterActionStatus.FAILED
                && action.getStatus() != AdapterActionStatus.CANCELLED
                && action.getStatus() != AdapterActionStatus.EXECUTOR_UNAVAILABLE
                && action.getStatus() != AdapterActionStatus.RETRY_WAITING) {
            throw new IllegalStateException("Only FAILED, CANCELLED, EXECUTOR_UNAVAILABLE or RETRY_WAITING adapter action can be retried: " + actionId);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        action.setStatus(AdapterActionStatus.PENDING);
        action.setReason(reason == null || reason.isBlank() ? "Manual adapter action retry" : reason);
        action.setNextAttemptAt(now);
        action.setRetryWaitingAt(null);
        action.setExecutorUnavailableAt(null);
        action.setFailedAt(null);
        action.setUpdatedAt(now);
        action.setLastError(null);
        if (resetAttempts) {
            action.setAttemptCount(0);
        }
        action.setMaxAttempts(effectiveMaxAttempts(action));
        clearClaim(action);
        return saveAndRecord(action, "manual_retry");
    }

    public AdapterAction cancel(String actionId, String reason) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        if (isTerminal(action.getStatus())) {
            throw new IllegalStateException("Adapter action is already terminal: " + actionId + " status=" + action.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        action.setStatus(AdapterActionStatus.CANCELLED);
        action.setReason(reason == null || reason.isBlank() ? "Cancelled by reviewer" : reason);
        action.setUpdatedAt(now);
        clearClaim(action);
        return saveAndRecord(action, "manual_cancel");
    }

    private AdapterAction recoverExpiredWorkerLease(AdapterAction action, OffsetDateTime now) {
        ClaimOwnership ownership = ownership(action);
        action.setAttemptCount(action.getAttemptCount() + 1);
        action.setMaxAttempts(effectiveMaxAttempts(action));
        action.setLastError("External worker lease expired. workerId=" + action.getClaimedBy());
        action.setFailedAt(now);
        action.setUpdatedAt(now);
        applyWorkerFailureStatus(action, true, now, "Worker lease expired");
        PersistenceWriteVerifier.requireApplied(
                repository.recoverExpiredClaim(action, ownership, now),
                "recover expired adapter action lease");
        clearClaim(action);
        record(action, "lease_recovered");
        return action;
    }

    private void applyWorkerFailureStatus(AdapterAction action, boolean retryable, OffsetDateTime now, String reasonPrefix) {
        boolean canRetry = properties.getWorker().isRetryEnabled()
                && retryable
                && action.getAttemptCount() < effectiveMaxAttempts(action);
        if (canRetry) {
            action.setStatus(AdapterActionStatus.RETRY_WAITING);
            action.setRetryWaitingAt(now);
            action.setNextAttemptAt(now.plus(workerBackoff(action.getAttemptCount())));
            action.setReason(reasonPrefix + "; retry scheduled at " + action.getNextAttemptAt());
        } else {
            action.setStatus(AdapterActionStatus.FAILED);
            action.setNextAttemptAt(null);
            action.setReason(reasonPrefix + "; marked FAILED" + (retryable ? " after max attempts" : " as non-retryable"));
        }
    }

    private int effectiveMaxAttempts(AdapterAction action) {
        return action.getMaxAttempts() > 0 ? action.getMaxAttempts() : properties.getWorker().getMaxAttempts();
    }

    private Duration workerBackoff(int attemptCount) {
        long initial = Math.max(1, properties.getWorker().getInitialBackoff().toMillis());
        long max = Math.max(initial, properties.getWorker().getMaxBackoff().toMillis());
        long multiplier = 1L << Math.max(0, Math.min(attemptCount - 1, 20));
        return Duration.ofMillis(Math.min(initial * multiplier, max));
    }

    @Override
    public Optional<AdapterAction> findById(String actionId) { return repository.findById(actionId); }

    @Override
    public String storeMode() { return repository.mode(); }

    @Override
    public List<AdapterAction> recent(int limit) { return repository.recent(limit); }
    public List<AdapterAction> byIncident(String incidentId, int limit) { return repository.findByIncidentId(incidentId, limit); }
    public List<AdapterAction> byTask(String taskId, int limit) { return repository.findByTaskId(taskId, limit); }
    public List<AdapterAction> byStatus(AdapterActionStatus status, int limit) { return repository.findByStatus(status, limit); }

    private String requireWorkerId(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        return workerId.trim();
    }

    private void ensureClaimedBy(AdapterAction action, String workerId) {
        String normalizedWorkerId = requireWorkerId(workerId);
        if (action.getStatus() != AdapterActionStatus.CLAIMED) {
            throw new IllegalStateException("Adapter action is not CLAIMED: " + action.getActionId() + " status=" + action.getStatus());
        }
        if (!normalizedWorkerId.equals(action.getClaimedBy())) {
            throw new IllegalStateException("Adapter action is claimed by another worker: " + action.getActionId());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (action.getLeaseExpiresAt() != null && action.getLeaseExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Adapter action lease expired: " + action.getActionId());
        }
    }

    private ClaimOwnership ownership(AdapterAction action) {
        return new ClaimOwnership(action.getClaimedBy(), action.getLeaseExpiresAt());
    }

    private Duration effectiveLease(Duration leaseDuration) {
        return leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofMinutes(2)
                : leaseDuration;
    }

    private void clearClaim(AdapterAction action) {
        action.setClaimedBy(null);
        action.setClaimedAt(null);
        action.setLeaseExpiresAt(null);
        action.setWorkerHeartbeatAt(null);
    }

    private AdapterAction createOrSuppress(AdapterType adapterType,
                                           AdapterActionType actionType,
                                           String adapterName,
                                           String idempotencyKey,
                                           TaskRecord task,
                                           DispatchRequest dispatchRequest,
                                           Incident incident,
                                           boolean enabled,
                                           String createReason,
                                           String suppressReason,
                                           TaskCallbackRequest callback,
                                           TaskCallbackType callbackType) {
        Optional<AdapterAction> previous = repository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            if (!properties.isCreateSuppressedRecords()) {
                return previous.get();
            }
            return saveAction(adapterType, actionType, adapterName, idempotencyKey + ":suppressed:" + UUID.randomUUID(), task, dispatchRequest, incident,
                    AdapterActionStatus.SUPPRESSED, "Duplicate adapter action suppressed. Existing actionId=" + previous.get().getActionId(), callback, callbackType);
        }
        if (!enabled) {
            if (!properties.isCreateSuppressedRecords()) {
                return null;
            }
            return saveAction(adapterType, actionType, adapterName, idempotencyKey + ":disabled:" + UUID.randomUUID(), task, dispatchRequest, incident,
                    AdapterActionStatus.SUPPRESSED, suppressReason, callback, callbackType);
        }
        return saveAction(adapterType, actionType, adapterName, idempotencyKey, task, dispatchRequest, incident,
                AdapterActionStatus.PENDING, createReason, callback, callbackType);
    }

    private AdapterAction saveAction(AdapterType adapterType,
                                     AdapterActionType actionType,
                                     String adapterName,
                                     String idempotencyKey,
                                     TaskRecord task,
                                     DispatchRequest dispatchRequest,
                                     Incident incident,
                                     AdapterActionStatus status,
                                     String reason,
                                     TaskCallbackRequest callback,
                                     TaskCallbackType callbackType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterAction action = new AdapterAction();
        String actionId = "act-" + UUID.randomUUID();
        action.setActionId(actionId);
        action.setIdempotencyKey(idempotencyKey);
        action.setIncidentId(task.getIncidentId());
        action.setTaskId(task.getTaskId());
        action.setDispatchRequestId(dispatchRequest == null ? null : dispatchRequest.getDispatchRequestId());
        action.setAssignmentId(dispatchRequest == null ? null : dispatchRequest.getAssignmentId());
        action.setAgentId(dispatchRequest == null ? null : dispatchRequest.getAgentId());
        action.setAdapterName(adapterName);
        action.setAdapterType(adapterType);
        action.setActionType(actionType);
        action.setStatus(status);
        action.setReason(reason);
        action.setRequestHash(sha256(idempotencyKey + "|" + actionType + "|" + safe(task.getTaskId())));
        action.setPayload(payload(task, dispatchRequest, incident, callback, callbackType, actionId, idempotencyKey));
        action.setMaxAttempts(properties.getWorker().getMaxAttempts());
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        if (status == AdapterActionStatus.PENDING) {
            return saveNewOrGetAndRecord(action, "create_or_get");
        }
        return saveAndRecord(action, status == AdapterActionStatus.SUPPRESSED ? "suppress" : "create");
    }

    private AdapterAction saveAndRecord(AdapterAction action, String operation) {
        AdapterAction saved = repository.save(action);
        record(saved, operation);
        recordIssueReadModel(saved, OffsetDateTime.now(ZoneOffset.UTC));
        return saved;
    }

    private AdapterAction saveNewOrGetAndRecord(AdapterAction action, String operation) {
        AdapterAction saved = repository.saveNewOrGetByIdempotencyKey(action);
        record(saved, operation);
        recordIssueReadModel(saved, OffsetDateTime.now(ZoneOffset.UTC));
        return saved;
    }

    private void recordIssueReadModel(AdapterAction action, OffsetDateTime observedAt) {
        if (action == null || action.getAdapterType() != AdapterType.ISSUE_TRACKING) return;
        try {
            TaskIssueLink link;
            if (action.getStatus() == AdapterActionStatus.COMPLETED) {
                link = TaskIssueLink.terminalFrom(action, null, null, null, "synced", TaskIssueLink.SYNCED, false, null, observedAt);
            } else if (action.getStatus() == AdapterActionStatus.FAILED || action.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
                link = TaskIssueLink.terminalFrom(action, null, null, null, "failed", TaskIssueLink.SYNC_FAILED, true,
                        firstNonBlank(action.getLastError(), action.getReason(), "Issue Tracking action failed"), observedAt);
            } else {
                link = TaskIssueLink.pendingFrom(action, observedAt);
            }
            taskIssueLinkRepository.save(link);
            log.info("issue_sync_read_model_saved taskId={} incidentId={} actionId={} adapterType={} actionType={} syncStatus={} issueVendor={} issueId={} issueUrl={} retryable={} error={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), action.getAdapterType(), action.getActionType(),
                    link.getSyncStatus(), link.getIssueVendor(), link.getIssueId(), link.getIssueUrl(), link.isIssueRetryable(), link.getSyncError());
        } catch (RuntimeException ex) {
            log.warn("issue_sync_read_model_failed taskId={} incidentId={} actionId={} reason={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), ex.getMessage());
            // Issue read model writes must not break task terminal handling or manual adapter operations.
        }
    }

    private void record(AdapterAction action, String operation) {
        if (metrics != null) {
            metrics.recordAdapterAction(action, operation);
        }
    }

    private Map<String, Object> payload(TaskRecord task,
                                        DispatchRequest dispatchRequest,
                                        Incident incident,
                                        TaskCallbackRequest callback,
                                        TaskCallbackType callbackType,
                                        String actionId,
                                        String idempotencyKey) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("adapterActionId", actionId);
        map.put("adapterActionIdempotencyKey", idempotencyKey);
        map.put("issueActionIdempotencyKey", idempotencyKey);
        map.put("issueCommentDedupeKey", issueCommentDedupeKey(task, dispatchRequest, callback, idempotencyKey));
        map.put("taskId", task.getTaskId());
        map.put("taskStatus", task.getStatus() == null ? null : task.getStatus().name());
        map.put("taskPriority", task.getPriority() == null ? null : task.getPriority().name());
        map.put("incidentId", task.getIncidentId());
        map.put("tenantId", task.getTenantId());
        map.put("sourceSystem", incident == null ? inferSystem(task) : incident.getSourceSystem());
        map.put("severity", severityLabel(task, incident));
        map.put("priority", severityLabel(task, incident));
        map.put("message", eventMessage(task, incident, callback));
        map.put("incidentMessage", incident == null ? null : incident.getLastMessage());
        map.put("createdReason", task.getCreatedReason());
        map.put("occurrenceCount", incident == null ? null : incident.getOccurrenceCount());
        map.put("siteId", task.getSiteId());
        map.put("plantId", task.getPlantId());
        map.put("objectType", task.getObjectType());
        map.put("objectId", task.getObjectId());
        map.put("eventType", task.getEventType());
        map.put("errorCode", task.getErrorCode());
        map.put("dispatchRequestId", dispatchRequest == null ? null : dispatchRequest.getDispatchRequestId());
        map.put("agentId", dispatchRequest == null ? null : dispatchRequest.getAgentId());
        map.put("ownerGatewayNodeId", dispatchRequest == null ? null : dispatchRequest.getOwnerGatewayNodeId());
        map.put("agentSessionId", dispatchRequest == null ? null : dispatchRequest.getAgentSessionId());
        map.put("linkedIssueId", incident == null ? null : incident.getLinkedIssueId());
        map.put("issueTitle", issueTitle(task, incident, callback));
        Map<String, Object> agentResult = AgentIssueHistoryFormatter.agentResult(task, dispatchRequest, incident, callback, callbackType);
        map.put("agentResult", agentResult);
        map.put("agentResultFormatVersion", agentResult.get("formatVersion"));
        map.put("agentSummary", agentResult.get("summary"));
        map.put("issueDescription", issueDescription(task, dispatchRequest, incident, callback, callbackType));
        map.put("issueComment", issueComment(task, dispatchRequest, incident, callback, callbackType));
        map.put("issueCommentMode", "APPEND");
        map.put("callbackType", callbackType == null ? null : callbackType.name());
        map.put("callbackId", callback == null ? null : callback.getCallbackId());
        map.put("callbackMessage", callback == null ? null : callback.getMessage());
        map.put("resultStatus", callback == null ? null : callback.getResultStatus());
        return map;
    }

    private String issueCommentDedupeKey(TaskRecord task,
                                         DispatchRequest dispatchRequest,
                                         TaskCallbackRequest callback,
                                         String fallbackKey) {
        String raw = "ISSUE_HISTORY|"
                + safe(task == null ? null : task.getIncidentId()) + "|"
                + safe(task == null ? null : task.getTaskId()) + "|"
                + safe(dispatchRequest == null ? null : dispatchRequest.getDispatchRequestId()) + "|"
                + safe(callback == null ? null : callback.getCallbackId()) + "|"
                + safe(fallbackKey);
        return sha256(raw);
    }

    private String issueTitle(TaskRecord task, Incident incident, TaskCallbackRequest callback) {
        String severity = severityLabel(task, incident);
        String system = firstNonBlank(incident == null ? null : incident.getSourceSystem(), inferSystem(task));
        String object = firstNonBlank(task.getObjectId(), incident == null ? null : incident.getObjectId(), task.getObjectType(), "target");
        String message = eventMessage(task, incident, callback);
        String event = firstNonBlank(task.getEventType(), incident == null ? null : incident.getEventType(), "event");
        String error = firstNonBlank(task.getErrorCode(), incident == null ? null : incident.getErrorCode());
        String suffix = object + " / " + event + (error == null || error.isBlank() ? "" : " / " + error);
        String title = "[" + severity + "][" + system + "] " + firstNonBlank(message, suffix) + " - " + suffix;
        return truncate(title, 240);
    }

    private String severityLabel(TaskRecord task, Incident incident) {
        return firstNonBlank(
                incident == null || incident.getSeverity() == null ? null : incident.getSeverity().name(),
                task == null || task.getPriority() == null ? null : task.getPriority().name(),
                "MEDIUM");
    }

    private String eventMessage(TaskRecord task, Incident incident, TaskCallbackRequest callback) {
        return firstNonBlank(
                incident == null ? null : incident.getLastMessage(),
                callback == null ? null : callback.getMessage(),
                task == null ? null : task.getCreatedReason(),
                task == null ? null : task.getErrorCode(),
                task == null ? null : task.getEventType());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String issueDescription(TaskRecord task, DispatchRequest dispatchRequest, Incident incident, TaskCallbackRequest callback, TaskCallbackType callbackType) {
        return AgentIssueHistoryFormatter.issueDescription(task, dispatchRequest, incident, callback, callbackType);
    }

    private String issueComment(TaskRecord task, DispatchRequest dispatchRequest, Incident incident, TaskCallbackRequest callback, TaskCallbackType callbackType) {
        return AgentIssueHistoryFormatter.issueComment(task, dispatchRequest, incident, callback, callbackType);
    }

    private String inferSystem(TaskRecord task) {
        return firstNonBlank(
                task == null ? null : task.getSourceSystem(),
                task == null ? null : task.getOriginSourceSystem(),
                "OpenDispatch");
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private boolean isTerminal(TaskStatus status) {
        return status != null && status.isTerminal();
    }

    private boolean isTerminal(AdapterActionStatus status) {
        return status == AdapterActionStatus.COMPLETED || status == AdapterActionStatus.FAILED || status == AdapterActionStatus.CANCELLED || status == AdapterActionStatus.SUPPRESSED;
    }

    private boolean shouldEvaluateMcp(boolean completed, boolean failed) {
        return (completed && properties.getMcp().isRunOnCompletedTask()) || (failed && properties.getMcp().isRunOnFailedTask());
    }

    private boolean shouldEvaluateIssue(boolean completed, boolean failed) {
        return (completed && properties.getIssue().isCreateOnCompletedTask()) || (failed && properties.getIssue().isCreateOnFailedTask());
    }

    private String mcpIdempotencyKey(TaskRecord task) {
        return properties.getMcp().isOnePerTask()
                ? sha256("MCP|" + task.getTaskId())
                : sha256("MCP|" + task.getIncidentId() + "|" + task.getTaskId() + "|" + UUID.randomUUID());
    }

    private String issueCreateIdempotencyKey(TaskRecord task, Incident incident) {
        String base = incident == null ? task.getIncidentId() : incident.getIncidentId();
        return properties.getIssue().isOneCreatePerIncident()
                ? sha256("ISSUE_CREATE|" + base)
                : sha256("ISSUE_CREATE|" + base + "|" + task.getTaskId());
    }

    private String issueUpdateIdempotencyKey(TaskRecord task, Incident incident) {
        String base = incident == null ? task.getIncidentId() : incident.getIncidentId();
        return properties.getIssue().isOneUpdatePerTask()
                ? sha256("ISSUE_UPDATE|" + base + "|" + task.getTaskId())
                : sha256("ISSUE_UPDATE|" + base + "|" + UUID.randomUUID());
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String safe(String value) { return value == null ? "" : value; }
    @Override
    public Map<String, Integer> statusCounts(int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AdapterActionStatus status : AdapterActionStatus.values()) {
            counts.put(status.name(), repository.findByStatus(status, Math.max(1, limit)).size());
        }
        return counts;
    }

    @Override
    public List<AdapterExecutorAuditRecord> auditByAction(String actionId, int limit) {
        return auditRepository.findByActionId(actionId, Math.max(1, limit));
    }

    @Override
    public List<AdapterExecutorAuditRecord> recentExecutorAudit(int limit) {
        return auditRepository.recent(Math.max(1, limit));
    }

    @Override
    public String executorAuditStoreMode() {
        return auditRepository.mode();
    }

}
