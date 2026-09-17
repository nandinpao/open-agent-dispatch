package com.opensocket.aievent.core.action.executor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionRepository;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditService;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.issue.provider.IssueLinkProjectionStatus;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionDisposition;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionResult;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionResultRepository;

@Service
public class AdapterActionExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AdapterActionExecutionService.class);

    private final AdapterActionRepository repository;
    private final List<AdapterActionExecutor> executors;
    private final AdapterActionExecutionProperties properties;
    private final AdapterExecutorCircuitBreaker circuitBreaker;
    private final AdapterExecutorAuditService auditService;
    private final IncidentFacade incidentFacade;
    private final AdapterExecutionAuthorityPolicy authorityPolicy;

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

    @Autowired(required = false)
    private IssueProviderExecutionResultRepository issueProviderResultRepository = IssueProviderExecutionResultRepository.noop();

    @Autowired
    public AdapterActionExecutionService(AdapterActionRepository repository,
                                         List<AdapterActionExecutor> executors,
                                         AdapterActionExecutionProperties properties,
                                         AdapterExecutorCircuitBreaker circuitBreaker,
                                         AdapterExecutorAuditService auditService,
                                         IncidentFacade incidentFacade) {
        this.repository = repository;
        this.executors = executors == null ? List.of() : List.copyOf(executors);
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.auditService = auditService;
        this.incidentFacade = incidentFacade;
        this.authorityPolicy = new AdapterExecutionAuthorityPolicy(properties);
        log.info("adapter_action_executor_runtime_config mode={} effectiveEmbeddedEnabled={} globalAutoExecutePending={} issueExecutionAuthority={} issueAutoExecutePending={} issueConnectorRuntimeEnabled={} issueLinkProjectionReconciliationEnabled={} issueLinkProjectionMaxAttempts={} batchSize={} executorCount={} mockEnabled={} issueDefaultVendor={}",
                properties.getMode(),
                properties.isEnabled(),
                properties.isAutoExecutePending(),
                properties.getIssue().getExecutionAuthority(),
                properties.getIssue().isAutoExecutePending(),
                properties.getIssue().isConnectorRuntimeEnabled(),
                properties.getIssue().isLinkProjectionReconciliationEnabled(),
                properties.getIssue().getLinkProjectionMaxAttempts(),
                properties.getBatchSize(),
                this.executors.size(),
                properties.getMock().isEnabled(),
                properties.getIssue().getDefaultVendor());
    }

    public AdapterActionExecutionService(AdapterActionRepository repository,
                                         List<AdapterActionExecutor> executors,
                                         AdapterActionExecutionProperties properties,
                                         AdapterExecutorCircuitBreaker circuitBreaker,
                                         AdapterExecutorAuditService auditService,
                                         IncidentFacade incidentFacade,
                                         TaskIssueLinkRepository taskIssueLinkRepository) {
        this(repository, executors, properties, circuitBreaker, auditService, incidentFacade);
        this.taskIssueLinkRepository = taskIssueLinkRepository == null ? TaskIssueLinkRepository.noop() : taskIssueLinkRepository;
    }

    public AdapterAction execute(String actionId) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        return executeAction(action);
    }

    public AdapterActionExecutionSummary executePending(int limit) {
        return executePending(limit, false);
    }

    /** Scheduler entry point: only adapter types with Core auto-execution authority are selected. */
    public AdapterActionExecutionSummary executeAutoPending(int limit) {
        return executePending(limit, true);
    }

    public boolean hasAutoExecutableAuthority() {
        for (AdapterType adapterType : AdapterType.values()) {
            if (authorityPolicy.shouldAutoExecuteInCore(adapterType)) return true;
        }
        return false;
    }

    private AdapterActionExecutionSummary executePending(int limit, boolean autoOnly) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int effectiveLimit = Math.max(1, Math.min(limit, properties.getBatchSize()));
        List<AdapterAction> pending = new ArrayList<>();
        for (AdapterType adapterType : AdapterType.values()) {
            if (!authorityPolicy.canCoreExecute(adapterType)) continue;
            if (autoOnly && !authorityPolicy.shouldAutoExecuteInCore(adapterType)) continue;
            pending.addAll(repository.findExecutablePendingByAdapterType(adapterType, now, effectiveLimit));
        }
        pending = pending.stream()
                .sorted(Comparator.comparing(AdapterAction::getCreatedAt))
                .limit(effectiveLimit)
                .toList();
        log.info("adapter_action_execute_pending_scan requestedLimit={} effectiveBatchSize={} autoOnly={} claimableCount={} issueAuthority={} mcpAuthority={}",
                limit, effectiveLimit, autoOnly, pending.size(),
                authorityPolicy.authorityFor(AdapterType.ISSUE_TRACKING), authorityPolicy.authorityFor(AdapterType.MCP));
        AdapterActionExecutionSummary summary = new AdapterActionExecutionSummary();
        summary.setRequested(pending.size());
        List<AdapterAction> processed = new ArrayList<>();
        int completed = 0;
        int failed = 0;
        int retryScheduled = 0;
        for (AdapterAction action : pending) {
            AdapterAction updated = executeAction(action);
            processed.add(updated);
            if (updated.getStatus() == AdapterActionStatus.COMPLETED) completed++;
            if (updated.getStatus() == AdapterActionStatus.FAILED) failed++;
            if ((updated.getStatus() == AdapterActionStatus.RETRY_WAITING || updated.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE) && updated.getAttemptCount() > 0) retryScheduled++;
        }
        summary.setExecuted(processed.size());
        summary.setCompleted(completed);
        summary.setFailed(failed);
        summary.setRetryScheduled(retryScheduled);
        summary.setActions(processed);
        return summary;
    }

    public AdapterAction reconcileUncertainIssueOutcome(String actionId,
                                                            String resolution,
                                                            String reason,
                                                            String issueId,
                                                            String issueUrl,
                                                            String issueStatus,
                                                            String responseRef) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        if (action.getAdapterType() != AdapterType.ISSUE_TRACKING || !mutatingIssueAction(action.getActionType())) {
            throw new IllegalStateException("ISSUE_PROVIDER_RECONCILIATION_MUTATING_ISSUE_ACTION_REQUIRED");
        }
        if (action.getStatus() != AdapterActionStatus.FAILED
                || action.getLastError() == null
                || !action.getLastError().contains("ISSUE_PROVIDER_OUTCOME_UNCERTAIN")) {
            throw new IllegalStateException("ISSUE_PROVIDER_RECONCILIATION_UNCERTAIN_OUTCOME_REQUIRED");
        }
        String decision = resolution == null ? "" : resolution.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"CONFIRMED_APPLIED".equals(decision) && !"CONFIRMED_NOT_APPLIED".equals(decision)) {
            throw new IllegalArgumentException("resolution must be CONFIRMED_APPLIED or CONFIRMED_NOT_APPLIED");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required for provider outcome reconciliation");
        }
        if ("CONFIRMED_APPLIED".equals(decision)
                && action.getActionType() == AdapterActionType.ISSUE_CREATE
                && (issueId == null || issueId.isBlank())) {
            throw new IllegalArgumentException("issueId is required when reconciling an uncertain CREATE as applied");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterActionStatus before = action.getStatus();
        java.util.Map<String,Object> payload = new java.util.LinkedHashMap<>(action.getPayload() == null ? java.util.Map.of() : action.getPayload());
        payload.put("providerReconciliationDecision", decision);
        payload.put("providerReconciliationReason", reason.trim());
        payload.put("providerReconciledAt", now.toString());
        if (issueId != null && !issueId.isBlank()) payload.put("linkedIssueId", issueId.trim());
        if (issueUrl != null && !issueUrl.isBlank()) payload.put("issueUrl", issueUrl.trim());
        if (issueStatus != null && !issueStatus.isBlank()) payload.put("providerReconciledIssueStatus", issueStatus.trim());
        action.setPayload(payload);

        if ("CONFIRMED_APPLIED".equals(decision)) {
            action.setStatus(AdapterActionStatus.COMPLETED);
            action.setCompletedAt(now);
            action.setFailedAt(null);
            action.setNextAttemptAt(null);
            action.setLastError(null);
            action.setResponseRef(responseRef == null || responseRef.isBlank() ? "provider-reconciled-applied" : responseRef.trim());
            action.setReason("Provider outcome reconciled as applied: " + reason.trim());
            action.setUpdatedAt(now);
            AdapterAction saved = repository.save(action);
            AdapterExecutionResult result = AdapterExecutionResult.success("provider-outcome-reconciliation", saved.getResponseRef());
            result.setIssueVendor("REDMINE");
            result.setIssueId(firstNonBlank(issueId, payloadText(payload, "linkedIssueId", "issueId", "externalIssueId")));
            result.setIssueUrl(firstNonBlank(issueUrl, payloadText(payload, "issueUrl", "webUrl", "url")));
            result.setIssueStatus(firstNonBlank(issueStatus, "reconciled"));
            result.setErrorCode("ISSUE_PROVIDER_OUTCOME_RECONCILED_APPLIED");
            result.setProviderHealthImpact("HEALTHY");
            result.setProviderOutcomeCertainty("CONFIRMED");
            enrichReconciliationEvidence(result, saved);
            IssueProviderExecutionResult reconciliationEvidence = observeReconciliationResult(saved, result, "RECONCILIATION_APPLIED", now, false);
            recordIssueReadModel(saved, result, reconciliationEvidence, now);
            auditService.record(saved, before, saved.getStatus(), result, "Provider uncertain outcome reconciled as applied: " + reason.trim());
            return saved;
        }

        action.setStatus(AdapterActionStatus.PENDING);
        action.setFailedAt(null);
        action.setNextAttemptAt(now);
        action.setLastError(null);
        action.setReason("Provider outcome reconciled as not applied; retry is allowed: " + reason.trim());
        action.setUpdatedAt(now);
        AdapterAction saved = repository.save(action);
        recordIssueReadModel(saved, null, now);
        AdapterExecutionResult result = AdapterExecutionResult.success("provider-outcome-reconciliation", "confirmed-not-applied-retry-allowed");
        result.setErrorCode("ISSUE_PROVIDER_OUTCOME_RECONCILED_NOT_APPLIED");
        result.setProviderHealthImpact("HEALTHY");
        result.setProviderOutcomeCertainty("CONFIRMED");
        enrichReconciliationEvidence(result, saved);
        observeReconciliationResult(saved, result, "RECONCILIATION_NOT_APPLIED", now, true);
        auditService.record(saved, before, saved.getStatus(), result, "Provider uncertain outcome reconciled as not applied; retry allowed: " + reason.trim());
        return saved;
    }

    public AdapterAction retry(String actionId) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        if (action.getStatus() != AdapterActionStatus.FAILED && action.getStatus() != AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
            throw new IllegalStateException("Only FAILED or EXECUTOR_UNAVAILABLE adapter action can be retried: " + actionId);
        }
        if (action.getLastError() != null && action.getLastError().contains("ISSUE_PROVIDER_OUTCOME_UNCERTAIN")) {
            throw new IllegalStateException("ISSUE_PROVIDER_OUTCOME_UNCERTAIN_RECONCILIATION_REQUIRED: inspect Redmine before retrying this mutating operation");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterActionStatus before = action.getStatus();
        action.setStatus(AdapterActionStatus.PENDING);
        action.setNextAttemptAt(now);
        action.setUpdatedAt(now);
        action.setLastError(null);
        AdapterAction saved = repository.save(action);
        recordIssueReadModel(saved, null, now);
        auditService.record(saved, before, saved.getStatus(), AdapterExecutionResult.success("manual-retry", "retry-scheduled"), "Manual retry scheduled");
        return saved;
    }

    private AdapterAction executeAction(AdapterAction action) {
        log.info("adapter_action_execution_started actionId={} taskId={} incidentId={} adapterType={} actionType={} status={} attemptCount={}",
                action.getActionId(), action.getTaskId(), action.getIncidentId(), action.getAdapterType(), action.getActionType(), action.getStatus(), action.getAttemptCount());
        if (!authorityPolicy.canCoreExecute(action.getAdapterType())) {
            AdapterExecutionAuthority authority = authorityPolicy.authorityFor(action.getAdapterType());
            log.warn("adapter_action_execution_skipped actionId={} taskId={} adapterType={} authority={} reason={}",
                    action.getActionId(), action.getTaskId(), action.getAdapterType(), authority,
                    action.getAdapterType() == AdapterType.ISSUE_TRACKING
                            ? AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE
                            : AdapterExecutionAuthorityPolicy.CORE_EXECUTION_NOT_AUTHORIZED);
            authorityPolicy.requireCoreExecution(action.getAdapterType());
        }
        if (action.getStatus() != AdapterActionStatus.PENDING && action.getStatus() != AdapterActionStatus.RETRY_WAITING && action.getStatus() != AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
            throw new IllegalStateException("Adapter action must be executable to execute: " + action.getActionId() + " status=" + action.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (action.getNextAttemptAt() != null && action.getNextAttemptAt().isAfter(now)) {
            throw new IllegalStateException("Adapter action is waiting for backoff until " + action.getNextAttemptAt());
        }

        AdapterActionExecutor executor = findSupportingExecutor(action);

        if (executor == null) {
            log.warn("adapter_action_executor_missing actionId={} taskId={} adapterType={} actionType={}",
                    action.getActionId(), action.getTaskId(), action.getAdapterType(), action.getActionType());
            return markExecutorUnavailable(action, "No adapter executor supports action " + action.getActionId() + " type=" + action.getAdapterType());
        }

        if (circuitBreaker.isOpen(executor.name())) {
            log.warn("adapter_action_executor_circuit_open actionId={} taskId={} executor={} openUntil={}",
                    action.getActionId(), action.getTaskId(), executor.name(), circuitBreaker.openUntil(executor.name()));
            return markExecutorUnavailable(action, "Executor circuit is open until " + circuitBreaker.openUntil(executor.name()));
        }

        AdapterActionStatus before = action.getStatus();
        action.setStatus(AdapterActionStatus.EXECUTING);
        action.setExecutingAt(now);
        action.setUpdatedAt(now);
        action.setAttemptCount(action.getAttemptCount() + 1);
        action.setMaxAttempts(properties.getMaxAttempts());
        action.setExecutorName(executor.name());
        action = repository.save(action);
        recordIssueReadModel(action, null, now);
        auditService.record(action, before, action.getStatus(), AdapterExecutionResult.success(executor.name(), "executing"), "Adapter action moved to EXECUTING");

        log.info("adapter_action_executor_invoked actionId={} taskId={} executor={} adapterType={} actionType={} attemptNo={}",
                action.getActionId(), action.getTaskId(), executor.name(), action.getAdapterType(), action.getActionType(), action.getAttemptCount());
        AdapterExecutionResult result;
        try {
            long start = System.nanoTime();
            result = executor.execute(action);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            if (elapsedMs > properties.getExecutionTimeout().toMillis()) {
                // The executor already returned a concrete provider result. Do not overwrite a known
                // outcome with a synthetic timeout and accidentally make a mutating operation retryable.
                log.warn("adapter_action_executor_slow_result actionId={} taskId={} executor={} elapsedMs={} configuredTimeoutMs={} outcome={} providerOutcomeCertainty={}",
                        action.getActionId(), action.getTaskId(), executor.name(), elapsedMs, properties.getExecutionTimeout().toMillis(),
                        result == null ? null : result.getOutcome(), result == null ? null : result.getProviderOutcomeCertainty());
            }
        } catch (AdapterExecutorUnavailableException ex) {
            result = AdapterExecutionResult.executorUnavailable(executor.name(), ex.getMessage());
        } catch (AdapterExecutorTimeoutException ex) {
            result = AdapterExecutionResult.timeout(executor.name(), ex.getMessage());
        } catch (Exception ex) {
            result = AdapterExecutionResult.retryableFailure(executor.name(), ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        }

        OffsetDateTime finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        ProviderEvidenceObservation providerObservation = observeProviderResult(action, result, finishedAt);
        result = providerObservation.executionResult();
        IssueProviderExecutionResult providerEvidence = providerObservation.evidence();
        action.setUpdatedAt(finishedAt);
        action.setExecutorName(result.getExecutorName() == null ? executor.name() : result.getExecutorName());
        if (result.isSuccess()) {
            circuitBreaker.recordSuccess(executor.name());
            action.setStatus(AdapterActionStatus.COMPLETED);
            action.setCompletedAt(finishedAt);
            action.setFailedAt(null);
            action.setNextAttemptAt(null);
            action.setResponseRef(result.getResponseRef());
            action.setLastError(null);
            AdapterAction saved = repository.save(action);
            log.info("adapter_action_execution_completed actionId={} taskId={} adapterType={} actionType={} executor={} status={} issueVendor={} issueId={} issueUrl={} responseRef={}",
                    saved.getActionId(), saved.getTaskId(), saved.getAdapterType(), saved.getActionType(), saved.getExecutorName(), saved.getStatus(),
                    result.getIssueVendor(), result.getIssueId(), result.getIssueUrl(), result.getResponseRef());
            linkIssueIfCreated(saved, result);
            recordIssueReadModel(saved, result, providerEvidence, finishedAt);
            auditService.record(saved, AdapterActionStatus.EXECUTING, saved.getStatus(), result, "Adapter action completed");
            return saved;
        }

        if (countsAsCircuitFailure(result)) {
            circuitBreaker.recordFailure(executor.name());
        } else {
            // Provider-level business denial/validation/rate-limit is not an executor outage.
            circuitBreaker.recordSuccess(executor.name());
        }
        action.setLastError(result.getError());
        AdapterAction saved = applyFailure(action, result, finishedAt);
        log.warn("adapter_action_execution_failed actionId={} taskId={} adapterType={} actionType={} executor={} status={} retryable={} error={}",
                saved.getActionId(), saved.getTaskId(), saved.getAdapterType(), saved.getActionType(), saved.getExecutorName(), saved.getStatus(), result.isRetryable(), result.getError());
        recordIssueReadModel(saved, result, providerEvidence, finishedAt);
        auditService.record(saved, AdapterActionStatus.EXECUTING, saved.getStatus(), result, result.getError());
        return saved;
    }

    private void recordIssueReadModel(AdapterAction action, AdapterExecutionResult result, OffsetDateTime observedAt) {
        recordIssueReadModel(action, result, null, observedAt);
    }

    /**
     * Projects AdapterAction/provider evidence into TaskIssueLink. Provider execution remains
     * authoritative: a read-model failure schedules projection-only reconciliation and must
     * never cause the provider operation to run again.
     */
    private boolean recordIssueReadModel(AdapterAction action,
                                         AdapterExecutionResult result,
                                         IssueProviderExecutionResult providerEvidence,
                                         OffsetDateTime observedAt) {
        if (action == null || action.getAdapterType() != AdapterType.ISSUE_TRACKING) return true;
        log.info("task_issue_link_persist_started taskId={} incidentId={} actionId={} actionStatus={} resultOutcome={} providerResultId={} providerDisposition={}",
                action.getTaskId(), action.getIncidentId(), action.getActionId(), action.getStatus(),
                result == null ? null : result.getOutcome(),
                providerEvidence == null ? null : providerEvidence.getResultId(),
                providerEvidence == null ? null : providerEvidence.getDisposition());
        try {
            TaskIssueLink link;
            if (result == null && action.getStatus() == AdapterActionStatus.EXECUTING) {
                link = TaskIssueLink.inProgressFrom(action, observedAt);
            } else if (result != null && result.isSuccess()) {
                link = TaskIssueLink.terminalFrom(
                        action,
                        result.getIssueVendor(),
                        result.getIssueId(),
                        result.getIssueUrl(),
                        result.getIssueStatus(),
                        TaskIssueLink.SYNCED,
                        false,
                        null,
                        observedAt);
            } else {
                String error = result == null ? action.getLastError() : result.getError();
                String syncStatus = issueFailureSyncStatus(action, result);
                boolean linkRetryable = TaskIssueLink.SYNC_FAILED_RETRYABLE.equals(syncStatus);
                link = TaskIssueLink.terminalFrom(
                        action,
                        null,
                        null,
                        null,
                        action.getStatus() == null ? null : action.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                        syncStatus,
                        linkRetryable,
                        error,
                        observedAt);
            }
            if (result != null) {
                link.setProviderFailureCode(result.getErrorCode());
                link.setProviderStatusCode(result.getProviderStatusCode());
                link.setProviderHealthImpact(result.getProviderHealthImpact());
                link.setProviderOutcomeCertainty(result.getProviderOutcomeCertainty());
                link.setOperationFingerprint(result.getOperationFingerprint());
                link.setCorrelationId(result.getCorrelationId());
                link.setA2aRequestId(result.getA2aRequestId());
                link.setSourceSystemId(result.getSourceSystemId());
                link.setConnectionId(result.getConnectionId());
                link.setProjectMappingId(result.getProjectMappingId());
                link.setExternalProjectId(result.getExternalProjectId());
                link.setTechnicalPrincipalId(result.getTechnicalPrincipalId());
                link.setCredentialId(result.getCredentialId());
                link.setCredentialVersion(result.getCredentialVersion());
                if (result.getIdempotencyKey() != null) link.setIdempotencyKey(result.getIdempotencyKey());
                if (result.getTenantId() != null) link.setTenantId(result.getTenantId());
            }
            preservePreviousProviderEvidence(link);
            taskIssueLinkRepository.save(link);
            markProviderProjectionSucceeded(providerEvidence, observedAt);
            log.info("task_issue_link_persist_completed taskId={} incidentId={} actionId={} linkId={} syncStatus={} issueVendor={} issueId={} issueUrl={} providerStatusCode={} providerOutcomeCertainty={} retryable={} providerResultId={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), link.getLinkId(), link.getSyncStatus(), link.getIssueVendor(), link.getIssueId(), link.getIssueUrl(),
                    link.getProviderStatusCode(), link.getProviderOutcomeCertainty(), link.isIssueRetryable(), providerEvidence == null ? null : providerEvidence.getResultId());
            log.info("issue_sync_read_model_saved taskId={} incidentId={} actionId={} adapterType={} actionType={} syncStatus={} issueVendor={} issueId={} issueUrl={} retryable={} error={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), action.getAdapterType(), action.getActionType(),
                    link.getSyncStatus(), link.getIssueVendor(), link.getIssueId(), link.getIssueUrl(), link.isIssueRetryable(), link.getSyncError());
            return true;
        } catch (RuntimeException ex) {
            log.warn("task_issue_link_persist_failed taskId={} incidentId={} actionId={} providerResultId={} exceptionClass={} reason={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), providerEvidence == null ? null : providerEvidence.getResultId(), ex.getClass().getName(), ex.getMessage());
            log.warn("issue_sync_read_model_failed taskId={} incidentId={} actionId={} reason={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), ex.getMessage());
            scheduleProviderProjectionRetry(providerEvidence, ex, observedAt);
            // Provider execution success/failure remains authoritative. Reconciliation retries only the link projection.
            return false;
        }
    }

    private ProviderEvidenceObservation observeProviderResult(AdapterAction action, AdapterExecutionResult result, OffsetDateTime observedAt) {
        if (action == null || result == null || action.getAdapterType() != AdapterType.ISSUE_TRACKING) {
            return new ProviderEvidenceObservation(result, null);
        }
        IssueProviderExecutionResult evidence = IssueProviderExecutionResult.observed(
                action, result, observedAt, properties.getIssue().getLinkProjectionMaxAttempts());
        evidence.setNextProjectionAttemptAt(observedAt.plus(properties.getIssue().getLinkProjectionReconciliationDelay()));
        if (evidence.getTenantId() == null || evidence.getTenantId().isBlank()) {
            log.warn("issue_provider_result_not_persisted actionId={} taskId={} attemptNo={} reason=TENANT_ID_MISSING",
                    action.getActionId(), action.getTaskId(), action.getAttemptCount());
            return new ProviderEvidenceObservation(result, null);
        }
        try {
            IssueProviderExecutionResult saved = issueProviderResultRepository.saveObserved(evidence);
            log.info("issue_provider_result_observed resultId={} actionId={} taskId={} attemptNo={} disposition={} provider={} externalIssueId={} providerStatusCode={} certainty={} projectionStatus={} repositoryMode={}",
                    saved == null ? evidence.getResultId() : saved.getResultId(), action.getActionId(), action.getTaskId(), action.getAttemptCount(),
                    evidence.getDisposition(), evidence.getProvider(), evidence.getExternalIssueId(), evidence.getProviderStatusCode(),
                    evidence.getProviderOutcomeCertainty(), evidence.getProjectionStatus(), issueProviderResultRepository.mode());
            return new ProviderEvidenceObservation(result, saved == null ? evidence : saved);
        } catch (RuntimeException ex) {
            captureProviderEvidenceFallback(action, result, observedAt, ex);
            log.error("issue_provider_result_persist_failed actionId={} taskId={} attemptNo={} provider={} issueId={} certainty={} exceptionClass={} reason={}",
                    action.getActionId(), action.getTaskId(), action.getAttemptCount(), result.getIssueVendor(), result.getIssueId(),
                    result.getProviderOutcomeCertainty(), ex.getClass().getName(), ex.getMessage());
            if (mutatingIssueAction(action.getActionType())
                    && (result.isSuccess() || result.getOutcome() == AdapterExecutionOutcome.OUTCOME_UNCERTAIN
                        || "UNCERTAIN".equalsIgnoreCase(result.getProviderOutcomeCertainty()))) {
                AdapterExecutionResult fenced = AdapterExecutionResult.outcomeUncertain(
                        result.getExecutorName(),
                        "ISSUE_PROVIDER_RESULT_PERSIST_FAILED_AFTER_PROVIDER_OBSERVATION: provider side effect must be reconciled before retry; " + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
                copyExecutionEvidence(result, fenced);
                fenced.setErrorCode("ISSUE_PROVIDER_RESULT_PERSIST_FAILED_AFTER_PROVIDER_OBSERVATION");
                fenced.setProviderOutcomeCertainty("UNCERTAIN");
                fenced.setRetryable(false);
                action.setResponseRef(result.getResponseRef());
                return new ProviderEvidenceObservation(fenced, null);
            }
            return new ProviderEvidenceObservation(result, null);
        }
    }

    private IssueProviderExecutionResult observeReconciliationResult(AdapterAction action,
                                                                      AdapterExecutionResult result,
                                                                      String observationKind,
                                                                      OffsetDateTime observedAt,
                                                                      boolean notApplied) {
        if (action == null || result == null || action.getAdapterType() != AdapterType.ISSUE_TRACKING) return null;
        try {
            IssueProviderExecutionResult evidence = IssueProviderExecutionResult.reconciled(
                    action, result, observationKind, observedAt, properties.getIssue().getLinkProjectionMaxAttempts());
            evidence.setNextProjectionAttemptAt(observedAt.plus(properties.getIssue().getLinkProjectionReconciliationDelay()));
            if (notApplied) {
                evidence.setDisposition(IssueProviderExecutionDisposition.CONFIRMED_FAILURE);
                evidence.setProjectionStatus(IssueLinkProjectionStatus.NOT_REQUIRED);
                evidence.setNextProjectionAttemptAt(null);
                evidence.setLastProjectionError("PROVIDER_RECONCILED_NOT_APPLIED_RETRY_ALLOWED");
            }
            IssueProviderExecutionResult saved = issueProviderResultRepository.saveObserved(evidence);
            if (notApplied && saved != null && saved.getProjectionStatus() != IssueLinkProjectionStatus.NOT_REQUIRED) {
                saved = issueProviderResultRepository.markProjectionNotRequired(saved.getResultId(),
                        "PROVIDER_RECONCILED_NOT_APPLIED_RETRY_ALLOWED", observedAt);
            }
            log.info("issue_provider_reconciliation_result_observed resultId={} actionId={} taskId={} observationKind={} disposition={} issueId={} projectionStatus={}",
                    saved == null ? evidence.getResultId() : saved.getResultId(), action.getActionId(), action.getTaskId(), observationKind,
                    evidence.getDisposition(), evidence.getExternalIssueId(), evidence.getProjectionStatus());
            return saved == null ? evidence : saved;
        } catch (RuntimeException ex) {
            captureProviderEvidenceFallback(action, result, observedAt, ex);
            log.error("issue_provider_reconciliation_result_persist_failed actionId={} taskId={} observationKind={} issueId={} reason={}",
                    action.getActionId(), action.getTaskId(), observationKind, result.getIssueId(), ex.getMessage());
            return null;
        }
    }

    /**
     * Replays only TaskIssueLink materialization from durable provider evidence. It never invokes
     * an AdapterActionExecutor or provider connector. Older attempts are marked NOT_REQUIRED once
     * a newer provider attempt exists, preventing stale failure evidence from overwriting success.
     */
    public int reconcileIssueLinkProjections(int requestedLimit) {
        if (!properties.getIssue().isLinkProjectionReconciliationEnabled()) return 0;
        int limit = Math.max(1, Math.min(requestedLimit, properties.getIssue().getLinkProjectionBatchSize()));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<IssueProviderExecutionResult> due = issueProviderResultRepository.findProjectionDue(now, limit);
        int projected = 0;
        for (IssueProviderExecutionResult evidence : due) {
            if (evidence == null || evidence.getAdapterActionId() == null) continue;
            AdapterAction action = repository.findById(evidence.getAdapterActionId()).orElse(null);
            if (action == null) {
                scheduleProviderProjectionRetry(evidence, new IllegalStateException("ADAPTER_ACTION_NOT_FOUND"), now);
                continue;
            }
            if (action.getAttemptCount() > evidence.getAttemptNo()) {
                issueProviderResultRepository.markProjectionNotRequired(
                        evidence.getResultId(),
                        "SUPERSEDED_BY_ADAPTER_ATTEMPT_" + action.getAttemptCount(),
                        now);
                log.info("issue_provider_result_projection_superseded resultId={} actionId={} evidenceAttempt={} currentAttempt={}",
                        evidence.getResultId(), action.getActionId(), evidence.getAttemptNo(), action.getAttemptCount());
                continue;
            }
            AdapterExecutionResult result = executionResultFromEvidence(evidence);
            action = recoverActionTerminalStateFromProviderEvidence(action, result, evidence, now);
            if (recordIssueReadModel(action, result, evidence, now)) projected++;
        }
        if (!due.isEmpty()) {
            log.info("issue_link_projection_reconciliation_completed requestedLimit={} dueCount={} projectedCount={}",
                    limit, due.size(), projected);
        }
        return projected;
    }

    /**
     * Operator-safe TaskIssueLink repair. Replays only local read-model projection from the latest
     * durable CONFIRMED_SUCCESS provider evidence for the latest ISSUE_TRACKING action. This method
     * never invokes an AdapterActionExecutor or a provider connector.
     */
    public boolean reconcileIssueLinkProjectionForTask(String taskId) {
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId is required");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterAction action = repository.findByTaskId(taskId.trim(), 100).stream()
                .filter(value -> value.getAdapterType() == AdapterType.ISSUE_TRACKING)
                .max(Comparator
                        .comparing(AdapterAction::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(AdapterAction::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(AdapterAction::getActionId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (action == null) {
            log.info("issue_link_projection_operator_retry_skipped taskId={} reason=ISSUE_ACTION_NOT_FOUND", taskId);
            return false;
        }
        IssueProviderExecutionResult evidence = issueProviderResultRepository.findLatestByAction(action.getActionId()).orElse(null);
        if (evidence == null || evidence.getDisposition() != IssueProviderExecutionDisposition.CONFIRMED_SUCCESS
                || evidence.getExternalIssueId() == null || evidence.getExternalIssueId().isBlank()) {
            log.info("issue_link_projection_operator_retry_skipped taskId={} actionId={} resultId={} reason=CONFIRMED_SUCCESS_PROVIDER_EVIDENCE_REQUIRED",
                    taskId, action.getActionId(), evidence == null ? null : evidence.getResultId());
            return false;
        }
        if (action.getAttemptCount() > evidence.getAttemptNo()) {
            issueProviderResultRepository.markProjectionNotRequired(
                    evidence.getResultId(), "SUPERSEDED_BY_ADAPTER_ATTEMPT_" + action.getAttemptCount(), now);
            log.info("issue_link_projection_operator_retry_skipped taskId={} actionId={} resultId={} reason=SUPERSEDED_PROVIDER_EVIDENCE",
                    taskId, action.getActionId(), evidence.getResultId());
            return false;
        }
        boolean projected = recordIssueReadModel(action, executionResultFromEvidence(evidence), evidence, now);
        log.info("issue_link_projection_operator_retry_completed taskId={} actionId={} resultId={} projected={} providerIssueId={}",
                taskId, action.getActionId(), evidence.getResultId(), projected, evidence.getExternalIssueId());
        return projected;
    }

    private AdapterAction recoverActionTerminalStateFromProviderEvidence(AdapterAction action,
                                                                          AdapterExecutionResult result,
                                                                          IssueProviderExecutionResult evidence,
                                                                          OffsetDateTime now) {
        if (action.getStatus() != AdapterActionStatus.EXECUTING) return action;
        AdapterActionStatus before = action.getStatus();
        if (evidence.getDisposition() == IssueProviderExecutionDisposition.CONFIRMED_SUCCESS) {
            action.setStatus(AdapterActionStatus.COMPLETED);
            action.setCompletedAt(evidence.getExecutedAt() == null ? now : evidence.getExecutedAt());
            action.setFailedAt(null);
            action.setNextAttemptAt(null);
            action.setLastError(null);
            action.setResponseRef(evidence.getResponseRef());
        } else if (evidence.getDisposition() == IssueProviderExecutionDisposition.UNKNOWN) {
            action.setStatus(AdapterActionStatus.FAILED);
            action.setFailedAt(now);
            action.setNextAttemptAt(null);
            action.setLastError("ISSUE_PROVIDER_OUTCOME_UNCERTAIN: durable provider evidence requires reconciliation before retry");
        } else {
            action.setLastError(result.getError());
            action = applyFailure(action, result, now);
            log.warn("adapter_action_terminal_state_recovered_from_provider_result actionId={} resultId={} before={} after={}",
                    action.getActionId(), evidence.getResultId(), before, action.getStatus());
            return action;
        }
        action.setUpdatedAt(now);
        AdapterAction saved = repository.save(action);
        log.warn("adapter_action_terminal_state_recovered_from_provider_result actionId={} resultId={} before={} after={}",
                saved.getActionId(), evidence.getResultId(), before, saved.getStatus());
        return saved;
    }

    private AdapterExecutionResult executionResultFromEvidence(IssueProviderExecutionResult evidence) {
        AdapterExecutionResult result;
        if (evidence.getDisposition() == IssueProviderExecutionDisposition.CONFIRMED_SUCCESS) {
            result = AdapterExecutionResult.success("provider-result-reconciliation", evidence.getResponseRef());
        } else if (evidence.getDisposition() == IssueProviderExecutionDisposition.UNKNOWN) {
            result = AdapterExecutionResult.outcomeUncertain("provider-result-reconciliation", firstNonBlank(evidence.getErrorMessage(), "ISSUE_PROVIDER_OUTCOME_UNCERTAIN"));
        } else if (evidence.isRetryable()) {
            result = AdapterExecutionResult.retryableFailure("provider-result-reconciliation", evidence.getErrorMessage());
        } else {
            result = AdapterExecutionResult.permanentFailure("provider-result-reconciliation", evidence.getErrorMessage());
        }
        result.setIssueVendor(evidence.getProvider());
        result.setIssueId(evidence.getExternalIssueId());
        result.setIssueUrl(evidence.getExternalIssueUrl());
        result.setIssueStatus(evidence.getExternalStatus());
        result.setProviderStatusCode(evidence.getProviderStatusCode());
        result.setProviderHealthImpact(evidence.getProviderHealthImpact());
        result.setErrorCode(evidence.getFailureCode());
        result.setProviderOutcomeCertainty(evidence.getProviderOutcomeCertainty());
        result.setIdempotencyKey(evidence.getIdempotencyKey());
        result.setOperationFingerprint(evidence.getOperationFingerprint());
        result.setTenantId(evidence.getTenantId());
        result.setCorrelationId(evidence.getCorrelationId());
        result.setA2aRequestId(evidence.getA2aRequestId());
        result.setSourceSystemId(evidence.getSourceSystemId());
        result.setConnectionId(evidence.getConnectionId());
        result.setProjectMappingId(evidence.getProjectMappingId());
        result.setExternalProjectId(evidence.getExternalProjectId());
        result.setTechnicalPrincipalId(evidence.getTechnicalPrincipalId());
        result.setCredentialId(evidence.getCredentialId());
        result.setCredentialVersion(evidence.getCredentialVersion());
        return result;
    }

    private void markProviderProjectionSucceeded(IssueProviderExecutionResult evidence, OffsetDateTime observedAt) {
        if (evidence == null || evidence.getResultId() == null || "NOOP".equalsIgnoreCase(issueProviderResultRepository.mode())) return;
        try {
            issueProviderResultRepository.markProjectionSucceeded(evidence.getResultId(), observedAt);
            log.info("issue_provider_result_projection_completed resultId={} actionId={} taskId={}",
                    evidence.getResultId(), evidence.getAdapterActionId(), evidence.getTaskId());
        } catch (RuntimeException ex) {
            // Link is already durable. Leaving provider evidence pending is safe: reconciliation is idempotent.
            log.warn("issue_provider_result_projection_ack_failed resultId={} actionId={} reason={}",
                    evidence.getResultId(), evidence.getAdapterActionId(), ex.getMessage());
        }
    }

    private void scheduleProviderProjectionRetry(IssueProviderExecutionResult evidence, RuntimeException failure, OffsetDateTime observedAt) {
        if (evidence == null || evidence.getResultId() == null || "NOOP".equalsIgnoreCase(issueProviderResultRepository.mode())) return;
        int attempt = evidence.getProjectionAttemptCount() + 1;
        String error = firstNonBlank(failure == null ? null : failure.getMessage(), failure == null ? null : failure.getClass().getName(), "TASK_ISSUE_LINK_PROJECTION_FAILED");
        try {
            if (attempt >= evidence.getProjectionMaxAttempts()) {
                issueProviderResultRepository.markProjectionFailedPermanent(evidence.getResultId(), attempt, error, observedAt);
                log.error("issue_provider_result_projection_failed_permanent resultId={} actionId={} taskId={} attempt={} maxAttempts={} reason={}",
                        evidence.getResultId(), evidence.getAdapterActionId(), evidence.getTaskId(), attempt, evidence.getProjectionMaxAttempts(), error);
            } else {
                OffsetDateTime next = observedAt.plus(issueProjectionBackoff(attempt));
                issueProviderResultRepository.markProjectionRetry(evidence.getResultId(), attempt, next, error);
                log.warn("issue_provider_result_projection_retry_scheduled resultId={} actionId={} taskId={} attempt={} maxAttempts={} nextAttemptAt={} reason={}",
                        evidence.getResultId(), evidence.getAdapterActionId(), evidence.getTaskId(), attempt, evidence.getProjectionMaxAttempts(), next, error);
            }
        } catch (RuntimeException updateFailure) {
            log.error("issue_provider_result_projection_state_update_failed resultId={} actionId={} originalReason={} updateReason={}",
                    evidence.getResultId(), evidence.getAdapterActionId(), error, updateFailure.getMessage());
        }
    }

    private Duration issueProjectionBackoff(int attempt) {
        long initial = Math.max(1, properties.getIssue().getLinkProjectionInitialBackoff().toMillis());
        long max = Math.max(initial, properties.getIssue().getLinkProjectionMaxBackoff().toMillis());
        long multiplier = 1L << Math.max(0, Math.min(attempt - 1, 20));
        return Duration.ofMillis(Math.min(initial * multiplier, max));
    }

    private void captureProviderEvidenceFallback(AdapterAction action, AdapterExecutionResult result, OffsetDateTime observedAt, RuntimeException failure) {
        java.util.Map<String,Object> payload = new java.util.LinkedHashMap<>(action.getPayload() == null ? java.util.Map.of() : action.getPayload());
        payload.put("providerEvidencePersistenceFailed", true);
        payload.put("providerEvidencePersistenceFailedAt", observedAt.toString());
        payload.put("providerEvidencePersistenceError", firstNonBlank(failure.getMessage(), failure.getClass().getName()));
        if (result.getIssueVendor() != null) payload.put("providerObservedVendor", result.getIssueVendor());
        if (result.getIssueId() != null) payload.put("providerObservedIssueId", result.getIssueId());
        if (result.getIssueUrl() != null) payload.put("providerObservedIssueUrl", result.getIssueUrl());
        if (result.getIssueStatus() != null) payload.put("providerObservedIssueStatus", result.getIssueStatus());
        if (result.getProviderStatusCode() != null) payload.put("providerObservedStatusCode", result.getProviderStatusCode());
        if (result.getProviderOutcomeCertainty() != null) payload.put("providerObservedOutcomeCertainty", result.getProviderOutcomeCertainty());
        if (result.getOperationFingerprint() != null) payload.put("providerObservedOperationFingerprint", result.getOperationFingerprint());
        if (result.getIdempotencyKey() != null) payload.put("providerObservedIdempotencyKey", result.getIdempotencyKey());
        action.setPayload(payload);
    }

    private void copyExecutionEvidence(AdapterExecutionResult source, AdapterExecutionResult target) {
        target.setIssueVendor(source.getIssueVendor());
        target.setIssueId(source.getIssueId());
        target.setIssueUrl(source.getIssueUrl());
        target.setIssueStatus(source.getIssueStatus());
        target.setProviderStatusCode(source.getProviderStatusCode());
        target.setProviderHealthImpact(source.getProviderHealthImpact());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setOperationFingerprint(source.getOperationFingerprint());
        target.setTenantId(source.getTenantId());
        target.setCorrelationId(source.getCorrelationId());
        target.setA2aRequestId(source.getA2aRequestId());
        target.setSourceSystemId(source.getSourceSystemId());
        target.setConnectionId(source.getConnectionId());
        target.setProjectMappingId(source.getProjectMappingId());
        target.setExternalProjectId(source.getExternalProjectId());
        target.setTechnicalPrincipalId(source.getTechnicalPrincipalId());
        target.setCredentialId(source.getCredentialId());
        target.setCredentialVersion(source.getCredentialVersion());
        target.setResponseRef(source.getResponseRef());
    }

    private record ProviderEvidenceObservation(AdapterExecutionResult executionResult, IssueProviderExecutionResult evidence) {}

    /**
     * Keep the Java read-model transition aligned with the database transition guard.
     * Once execution has entered IN_PROGRESS, a retryable execution failure must be
     * projected as FAILED_RETRYABLE; moving back to PENDING is explicitly forbidden.
     */
    private String issueFailureSyncStatus(AdapterAction action, AdapterExecutionResult result) {
        AdapterActionStatus status = action == null ? null : action.getStatus();
        if (status == AdapterActionStatus.PENDING) return TaskIssueLink.SYNC_PENDING;
        if (status == AdapterActionStatus.RETRY_WAITING || status == AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
            return TaskIssueLink.SYNC_FAILED_RETRYABLE;
        }
        if (status == AdapterActionStatus.FAILED || status == AdapterActionStatus.CANCELLED) {
            return TaskIssueLink.SYNC_FAILED_PERMANENT;
        }
        if (result != null && result.isRetryable()) return TaskIssueLink.SYNC_FAILED_RETRYABLE;
        return TaskIssueLink.SYNC_FAILED_PERMANENT;
    }

    private void preservePreviousProviderEvidence(TaskIssueLink link) {
        if (link == null || link.getIdempotencyKey() == null || link.getIdempotencyKey().isBlank()) return;
        try {
            TaskIssueLink previous = taskIssueLinkRepository.findByTenantAndIdempotencyKey(link.getTenantId(), link.getIdempotencyKey()).orElse(null);
            if (previous == null) return;
            if (link.getTechnicalPrincipalId() == null) link.setTechnicalPrincipalId(previous.getTechnicalPrincipalId());
            if (link.getCredentialId() == null) link.setCredentialId(previous.getCredentialId());
            if (link.getCredentialVersion() == null) link.setCredentialVersion(previous.getCredentialVersion());
            if (link.getOperationFingerprint() == null) link.setOperationFingerprint(previous.getOperationFingerprint());
            if (link.getConnectionId() == null) link.setConnectionId(previous.getConnectionId());
            if (link.getProjectMappingId() == null) link.setProjectMappingId(previous.getProjectMappingId());
            if (link.getExternalProjectId() == null) link.setExternalProjectId(previous.getExternalProjectId());
            if (link.getCorrelationId() == null) link.setCorrelationId(previous.getCorrelationId());
            if (link.getA2aRequestId() == null) link.setA2aRequestId(previous.getA2aRequestId());
            if (link.getSourceSystemId() == null) link.setSourceSystemId(previous.getSourceSystemId());
        } catch (RuntimeException ignored) {
            // Reconciliation/read-model preservation is best effort and must never rewrite provider action authority.
        }
    }

    private void linkIssueIfCreated(AdapterAction action, AdapterExecutionResult result) {
        if (action == null || result == null) return;
        if (action.getAdapterType() != AdapterType.ISSUE_TRACKING || action.getActionType() != AdapterActionType.ISSUE_CREATE) return;
        if (action.getIncidentId() == null || action.getIncidentId().isBlank()) return;
        if (result.getIssueId() == null || result.getIssueId().isBlank()) return;
        String vendor = result.getIssueVendor() == null || result.getIssueVendor().isBlank() ? "ISSUE" : result.getIssueVendor();
        try {
            incidentFacade.linkIssueIfAbsent(action.getIncidentId(), vendor + ":" + result.getIssueId());
            log.info("issue_sync_incident_linked incidentId={} taskId={} actionId={} issueRef={}",
                    action.getIncidentId(), action.getTaskId(), action.getActionId(), vendor + ":" + result.getIssueId());
        } catch (UnsupportedOperationException ex) {
            log.warn("issue_sync_incident_link_skipped incidentId={} taskId={} actionId={} reason={}",
                    action.getIncidentId(), action.getTaskId(), action.getActionId(), ex.getMessage());
            // Some test facades intentionally do not implement incident issue links.
        }
    }

    private boolean mutatingIssueAction(AdapterActionType type) {
        return type == AdapterActionType.ISSUE_CREATE
                || type == AdapterActionType.ISSUE_UPDATE
                || type == AdapterActionType.ISSUE_COMMENT
                || type == AdapterActionType.ISSUE_UPDATE_COMMENT;
    }

    private String payloadText(java.util.Map<String,Object> payload, String... keys) {
        if (payload == null) return null;
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private void enrichReconciliationEvidence(AdapterExecutionResult result, AdapterAction action) {
        java.util.Map<String,Object> payload = action.getPayload();
        result.setTenantId(payloadText(payload, "tenantId"));
        result.setCorrelationId(payloadText(payload, "correlationId"));
        result.setSourceSystemId(payloadText(payload, "sourceSystemId", "sourceSystem", "executorDomainId"));
        result.setConnectionId(payloadText(payload, "connectionId"));
        result.setProjectMappingId(payloadText(payload, "projectMappingId"));
        result.setExternalProjectId(payloadText(payload, "externalProjectId", "projectId"));
        result.setIdempotencyKey(action.getIdempotencyKey());
    }

    private AdapterActionExecutor findSupportingExecutor(AdapterAction action) {
        for (AdapterActionExecutor executor : executors) {
            if (executor.supports(action)) {
                return executor;
            }
        }
        return null;
    }

    private AdapterAction markExecutorUnavailable(AdapterAction action, String error) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterActionStatus before = action.getStatus();
        action.setUpdatedAt(now);
        action.setLastError(error);
        action.setAttemptCount(action.getAttemptCount() + 1);
        action.setMaxAttempts(properties.getMaxAttempts());
        if (properties.isMarkUnavailableWhenNoExecutor() && action.getAttemptCount() < properties.getMaxAttempts()) {
            action.setStatus(AdapterActionStatus.EXECUTOR_UNAVAILABLE);
            action.setExecutorUnavailableAt(now);
            action.setNextAttemptAt(now.plus(backoff(action.getAttemptCount())));
        } else {
            action.setStatus(AdapterActionStatus.FAILED);
            action.setFailedAt(now);
            action.setNextAttemptAt(null);
        }
        AdapterAction saved = repository.save(action);
        AdapterExecutionResult result = AdapterExecutionResult.executorUnavailable("none", error);
        recordIssueReadModel(saved, result, now);
        auditService.record(saved, before, saved.getStatus(), result, error);
        return saved;
    }

    private AdapterAction applyFailure(AdapterAction action, AdapterExecutionResult result, OffsetDateTime finishedAt) {
        if (!result.isRetryable() || result.getOutcome() == AdapterExecutionOutcome.PERMANENT_FAILURE) {
            action.setStatus(AdapterActionStatus.FAILED);
            action.setFailedAt(finishedAt);
            action.setNextAttemptAt(null);
            return repository.save(action);
        }
        if (action.getAttemptCount() < properties.getMaxAttempts()) {
            if (result.getOutcome() == AdapterExecutionOutcome.EXECUTOR_UNAVAILABLE) {
                action.setStatus(AdapterActionStatus.EXECUTOR_UNAVAILABLE);
                action.setExecutorUnavailableAt(finishedAt);
            } else {
                action.setStatus(AdapterActionStatus.RETRY_WAITING);
                action.setRetryWaitingAt(finishedAt);
            }
            action.setNextAttemptAt(finishedAt.plus(backoff(action.getAttemptCount())));
        } else {
            action.setStatus(AdapterActionStatus.FAILED);
            action.setFailedAt(finishedAt);
            action.setNextAttemptAt(null);
        }
        return repository.save(action);
    }

    private boolean countsAsCircuitFailure(AdapterExecutionResult result) {
        if (result == null) return true;
        String impact = result.getProviderHealthImpact();
        if ("HEALTHY".equalsIgnoreCase(impact) || "THROTTLED".equalsIgnoreCase(impact) || "NONE".equalsIgnoreCase(impact)) {
            return false;
        }
        // Only provider/executor failures that can represent shared runtime degradation
        // participate in the executor circuit. Configuration/preflight failures explicitly
        // marked NONE must not open a global circuit for unrelated tenants/credentials.
        return !result.isSuccess();
    }

    private Duration backoff(int attemptCount) {
        long initial = Math.max(1, properties.getInitialBackoff().toMillis());
        long max = Math.max(initial, properties.getMaxBackoff().toMillis());
        long multiplier = 1L << Math.max(0, Math.min(attemptCount - 1, 20));
        long calculated = initial * multiplier;
        return Duration.ofMillis(Math.min(calculated, max));
    }
}
