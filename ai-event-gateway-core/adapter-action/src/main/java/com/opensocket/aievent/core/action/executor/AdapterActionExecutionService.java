package com.opensocket.aievent.core.action.executor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

@Service
public class AdapterActionExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AdapterActionExecutionService.class);

    private final AdapterActionRepository repository;
    private final List<AdapterActionExecutor> executors;
    private final AdapterActionExecutionProperties properties;
    private final AdapterExecutorCircuitBreaker circuitBreaker;
    private final AdapterExecutorAuditService auditService;
    private final IncidentFacade incidentFacade;

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

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
    }

    public AdapterAction execute(String actionId) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        return executeAction(action);
    }

    public AdapterActionExecutionSummary executePending(int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<AdapterAction> pending = repository.findExecutablePending(now, Math.max(1, Math.min(limit, properties.getBatchSize())));
        log.info("adapter_action_execute_pending_scan requestedLimit={} effectiveBatchSize={} claimableCount={}", limit, Math.max(1, Math.min(limit, properties.getBatchSize())), pending.size());
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

    public AdapterAction retry(String actionId) {
        AdapterAction action = repository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        if (action.getStatus() != AdapterActionStatus.FAILED && action.getStatus() != AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
            throw new IllegalStateException("Only FAILED or EXECUTOR_UNAVAILABLE adapter action can be retried: " + actionId);
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
        if (!properties.isEnabled()) {
            log.warn("adapter_action_execution_skipped actionId={} taskId={} reason={}", action.getActionId(), action.getTaskId(), "EXECUTOR_DISABLED");
            throw new IllegalStateException("Adapter executor is disabled");
        }
        if (action.getStatus() != AdapterActionStatus.PENDING && action.getStatus() != AdapterActionStatus.RETRY_WAITING && action.getStatus() != AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
            throw new IllegalStateException("Adapter action must be executable to execute: " + action.getActionId() + " status=" + action.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (action.getNextAttemptAt() != null && action.getNextAttemptAt().isAfter(now)) {
            throw new IllegalStateException("Adapter action is waiting for backoff until " + action.getNextAttemptAt());
        }

        AdapterActionExecutor executor = executors.stream()
                .filter(e -> e.supports(action))
                .findFirst()
                .orElse(null);

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
        repository.save(action);
        auditService.record(action, before, action.getStatus(), AdapterExecutionResult.success(executor.name(), "executing"), "Adapter action moved to EXECUTING");

        log.info("adapter_action_executor_invoked actionId={} taskId={} executor={} adapterType={} actionType={} attemptNo={}",
                action.getActionId(), action.getTaskId(), executor.name(), action.getAdapterType(), action.getActionType(), action.getAttemptCount());
        AdapterExecutionResult result;
        try {
            long start = System.nanoTime();
            result = executor.execute(action);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            if (elapsedMs > properties.getExecutionTimeout().toMillis()) {
                result = AdapterExecutionResult.timeout(executor.name(), "Executor exceeded configured timeout: " + elapsedMs + "ms");
            }
        } catch (AdapterExecutorUnavailableException ex) {
            result = AdapterExecutionResult.executorUnavailable(executor.name(), ex.getMessage());
        } catch (AdapterExecutorTimeoutException ex) {
            result = AdapterExecutionResult.timeout(executor.name(), ex.getMessage());
        } catch (Exception ex) {
            result = AdapterExecutionResult.retryableFailure(executor.name(), ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        }

        OffsetDateTime finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
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
            recordIssueReadModel(saved, result, finishedAt);
            auditService.record(saved, AdapterActionStatus.EXECUTING, saved.getStatus(), result, "Adapter action completed");
            return saved;
        }

        circuitBreaker.recordFailure(executor.name());
        action.setLastError(result.getError());
        AdapterAction saved = applyFailure(action, result, finishedAt);
        log.warn("adapter_action_execution_failed actionId={} taskId={} adapterType={} actionType={} executor={} status={} retryable={} error={}",
                saved.getActionId(), saved.getTaskId(), saved.getAdapterType(), saved.getActionType(), saved.getExecutorName(), saved.getStatus(), result.isRetryable(), result.getError());
        recordIssueReadModel(saved, result, finishedAt);
        auditService.record(saved, AdapterActionStatus.EXECUTING, saved.getStatus(), result, result.getError());
        return saved;
    }

    private void recordIssueReadModel(AdapterAction action, AdapterExecutionResult result, OffsetDateTime observedAt) {
        if (action == null || action.getAdapterType() != AdapterType.ISSUE_TRACKING) return;
        try {
            TaskIssueLink link;
            if (result != null && result.isSuccess()) {
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
                boolean retryable = result != null && result.isRetryable();
                link = TaskIssueLink.terminalFrom(
                        action,
                        null,
                        null,
                        null,
                        action.getStatus() == null ? null : action.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                        action.getStatus() == AdapterActionStatus.PENDING || action.getStatus() == AdapterActionStatus.RETRY_WAITING || action.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE
                                ? TaskIssueLink.SYNC_PENDING
                                : TaskIssueLink.SYNC_FAILED,
                        retryable || action.getStatus() == AdapterActionStatus.RETRY_WAITING || action.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE,
                        error,
                        observedAt);
            }
            taskIssueLinkRepository.save(link);
            log.info("issue_sync_read_model_saved taskId={} incidentId={} actionId={} adapterType={} actionType={} syncStatus={} issueVendor={} issueId={} issueUrl={} retryable={} error={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), action.getAdapterType(), action.getActionType(),
                    link.getSyncStatus(), link.getIssueVendor(), link.getIssueId(), link.getIssueUrl(), link.isIssueRetryable(), link.getSyncError());
        } catch (RuntimeException ex) {
            log.warn("issue_sync_read_model_failed taskId={} incidentId={} actionId={} reason={}",
                    action.getTaskId(), action.getIncidentId(), action.getActionId(), ex.getMessage());
            // Adapter execution success/failure must remain authoritative even if the read-model write fails.
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

    private Duration backoff(int attemptCount) {
        long initial = Math.max(1, properties.getInitialBackoff().toMillis());
        long max = Math.max(initial, properties.getMaxBackoff().toMillis());
        long multiplier = 1L << Math.max(0, Math.min(attemptCount - 1, 20));
        long calculated = initial * multiplier;
        return Duration.ofMillis(Math.min(calculated, max));
    }
}
