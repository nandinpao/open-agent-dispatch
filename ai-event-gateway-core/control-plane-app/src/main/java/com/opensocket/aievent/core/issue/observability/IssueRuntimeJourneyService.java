package com.opensocket.aievent.core.issue.observability;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionRepository;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterExecutionAuthority;
import com.opensocket.aievent.core.action.executor.AdapterExecutionAuthorityPolicy;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyAutomationStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyBindingStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecision;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionOutcome;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionRepository;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.issue.provider.IssueLinkProjectionStatus;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionDisposition;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionResult;
import com.opensocket.aievent.core.issue.provider.IssueProviderExecutionResultRepository;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Canonical Route B observability read authority.
 *
 * <p>This service never writes operational state. It reduces the persisted policy, binding,
 * AdapterAction, execution-authority, durable provider result and TaskIssueLink authorities into
 * the seven-stage vocabulary used by the API, Admin UI and log diagnostics.</p>
 */
@Service
public class IssueRuntimeJourneyService {
    public static final String AUTHORITY = "V39_4_ISSUE_RUNTIME_JOURNEY_V1";
    private static final String PRIMARY_PURPOSE = "PRIMARY_ISSUE";
    private static final Logger log = LoggerFactory.getLogger(IssueRuntimeJourneyService.class);

    private final TaskOperationalQuery taskQuery;
    private final IssuePolicyDecisionRepository policyDecisions;
    private final AdapterActionRepository adapterActions;
    private final AdapterExecutionAuthorityPolicy executionAuthorityPolicy;
    private final IssueProviderExecutionResultRepository providerResults;
    private final TaskIssueLinkRepository taskIssueLinks;

    public IssueRuntimeJourneyService(
            TaskOperationalQuery taskQuery,
            IssuePolicyDecisionRepository policyDecisions,
            AdapterActionRepository adapterActions,
            AdapterExecutionAuthorityPolicy executionAuthorityPolicy,
            IssueProviderExecutionResultRepository providerResults,
            TaskIssueLinkRepository taskIssueLinks) {
        this.taskQuery = taskQuery;
        this.policyDecisions = policyDecisions;
        this.adapterActions = adapterActions;
        this.executionAuthorityPolicy = executionAuthorityPolicy;
        this.providerResults = providerResults;
        this.taskIssueLinks = taskIssueLinks;
    }

    public IssueRuntimeJourneyView journey(String taskId) {
        TaskRecord task = taskQuery.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        String tenantId = task.getTenantId();
        IssuePolicyDecision policy = policyDecisions.findByTaskAndPurpose(tenantId, taskId, PRIMARY_PURPOSE).orElse(null);
        AdapterAction action = primaryIssueAction(taskId, policy);
        IssueProviderExecutionResult providerResult = action == null ? null
                : providerResults.findLatestByAction(action.getActionId()).orElse(null);
        TaskIssueLink link = primaryIssueLink(tenantId, taskId, action, providerResult);

        List<IssueRuntimeStage> stages = List.of(
                policyStage(task, policy),
                bindingStage(policy),
                actionStage(policy, action),
                executorStage(policy, action),
                providerStage(policy, action, providerResult),
                resultStage(policy, action, providerResult),
                linkStage(policy, action, providerResult, link));

        IssueRuntimeStage firstProblem = stages.stream().filter(stage -> stage.status().isProblem()).findFirst().orElse(null);
        IssueRuntimeStage current = firstProblem != null ? firstProblem : stages.stream()
                .filter(stage -> !stage.status().isTerminalSuccess())
                .findFirst().orElse(stages.get(stages.size() - 1));
        String overall = overallStatus(stages);
        long revision = revisionFor(
                task.getVersion(), task.getUpdatedAt(),
                policy == null ? null : policy.version(), policy == null ? null : policy.updatedAt(),
                action == null ? null : action.getActionId(), action == null ? null : action.getUpdatedAt(), action == null ? null : action.getStatus(),
                providerResult == null ? null : providerResult.getResultId(), providerResult == null ? null : providerResult.getUpdatedAt(), providerResult == null ? null : providerResult.getProjectionStatus(),
                link == null ? null : link.getLinkId(), link == null ? null : link.getResourceVersion(), link == null ? null : link.getUpdatedAt());

        IssueRuntimeJourneyView view = new IssueRuntimeJourneyView(
                tenantId, taskId,
                firstNonBlank(task.getCorrelationId(), task.getOriginCorrelationId(), task.getIncidentId(), taskId),
                overall,
                current.stage(),
                firstProblem == null ? null : firstProblem.stage(),
                firstProblem == null ? current.reasonCode() : firstProblem.reasonCode(),
                firstProblem == null ? current.summary() : firstProblem.summary(),
                revision,
                OffsetDateTime.now(ZoneOffset.UTC),
                stages);

        log.info("issue_runtime_journey_evaluated taskId={} overallStatus={} currentStage={} firstFailedStage={} reasonCode={} actionId={} providerResultId={} linkId={} authority={}",
                taskId, view.overallStatus(), view.currentStage(), view.firstFailedStage(), view.reasonCode(),
                action == null ? null : action.getActionId(), providerResult == null ? null : providerResult.getResultId(),
                link == null ? null : link.getLinkId(), AUTHORITY);
        return view;
    }

    private IssueRuntimeStage policyStage(TaskRecord task, IssuePolicyDecision policy) {
        if (policy == null) {
            boolean terminal = task.getStatus() != null && task.getStatus().isTerminal();
            return stage(IssueRuntimeStageCode.POLICY,
                    terminal ? IssueRuntimeStageStatus.PENDING : IssueRuntimeStageStatus.NOT_STARTED,
                    true, false,
                    terminal ? "ISSUE_POLICY_DECISION_PENDING" : "ISSUE_POLICY_WAITING_FOR_TERMINAL_TASK",
                    terminal ? "Task is terminal; waiting for the durable Issue policy decision."
                            : "Issue policy is evaluated after terminal Task completion/failure.",
                    task.getUpdatedAt(), null, List.of());
        }
        if (policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) {
            return stage(IssueRuntimeStageCode.POLICY, IssueRuntimeStageStatus.NOT_REQUIRED, false, false,
                    firstNonBlank(policy.reasonCode(), "ISSUE_NOT_REQUIRED"),
                    "Issue policy determined that no external Issue is required.", policy.updatedAt(), null, policyEvidence(policy));
        }
        if (policy.decision() == IssuePolicyDecisionOutcome.MANUAL_DECISION) {
            return stage(IssueRuntimeStageCode.POLICY, IssueRuntimeStageStatus.BLOCKED, true, false,
                    firstNonBlank(policy.reasonCode(), "ISSUE_MANUAL_DECISION_REQUIRED"),
                    "Issue policy requires an administrator decision before automation may continue.", policy.updatedAt(), null, policyEvidence(policy));
        }
        return stage(IssueRuntimeStageCode.POLICY, IssueRuntimeStageStatus.SUCCEEDED, true, false,
                firstNonBlank(policy.reasonCode(), "ISSUE_POLICY_REQUIRED"),
                "Issue policy requires Route B execution.", policy.updatedAt(), null, policyEvidence(policy));
    }

    private IssueRuntimeStage bindingStage(IssuePolicyDecision policy) {
        if (policy == null) return notStarted(IssueRuntimeStageCode.BINDING, "ISSUE_BINDING_WAITING_FOR_POLICY", "Binding waits for a durable Issue policy decision.");
        if (policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) return notRequired(IssueRuntimeStageCode.BINDING, policy.updatedAt());
        if (policy.decision() == IssuePolicyDecisionOutcome.MANUAL_DECISION) return notStarted(IssueRuntimeStageCode.BINDING, "ISSUE_BINDING_WAITING_FOR_MANUAL_DECISION", "Binding is not evaluated until the manual policy decision is resolved.");
        if (policy.bindingStatus() == IssuePolicyBindingStatus.RESOLVED) {
            return stage(IssueRuntimeStageCode.BINDING, IssueRuntimeStageStatus.SUCCEEDED, true, false,
                    "ISSUE_BINDING_RESOLVED", "Connection and project mapping are resolved for the canonical Issue owner.",
                    policy.updatedAt(), null, policyEvidence(policy));
        }
        if (policy.bindingStatus() == IssuePolicyBindingStatus.NOT_EVALUATED) {
            return stage(IssueRuntimeStageCode.BINDING, IssueRuntimeStageStatus.PENDING, true, false,
                    "ISSUE_BINDING_NOT_EVALUATED", "Issue binding has not been evaluated yet.", policy.updatedAt(), null, policyEvidence(policy));
        }
        return stage(IssueRuntimeStageCode.BINDING, IssueRuntimeStageStatus.BLOCKED, true, false,
                firstNonBlank(policy.lastErrorCode(), "ISSUE_BINDING_" + policy.bindingStatus().name()),
                firstNonBlank(policy.lastErrorMessage(), "Issue binding is blocked: " + policy.bindingStatus().name() + "."),
                policy.updatedAt(), null, policyEvidence(policy));
    }

    private IssueRuntimeStage actionStage(IssuePolicyDecision policy, AdapterAction action) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) return notRequired(IssueRuntimeStageCode.ACTION, policy.updatedAt());
        if (policy == null || policy.decision() == IssuePolicyDecisionOutcome.MANUAL_DECISION || policy.bindingStatus() != IssuePolicyBindingStatus.RESOLVED) {
            return notStarted(IssueRuntimeStageCode.ACTION, "ISSUE_ACTION_WAITING_FOR_BINDING", "AdapterAction is created only after Issue policy and binding are resolved.");
        }
        if (action == null) {
            boolean failed = policy.automationStatus() == IssuePolicyAutomationStatus.FAILED;
            return stage(IssueRuntimeStageCode.ACTION,
                    failed ? IssueRuntimeStageStatus.FAILED_RETRYABLE : IssueRuntimeStageStatus.PENDING,
                    true, failed,
                    firstNonBlank(policy.lastErrorCode(), failed ? "ISSUE_ADAPTER_ACTION_REQUEST_FAILED" : "ISSUE_ADAPTER_ACTION_NOT_CREATED"),
                    firstNonBlank(policy.lastErrorMessage(), failed ? "AdapterAction request failed and is eligible for retry." : "Waiting for the durable ISSUE_TRACKING AdapterAction."),
                    policy.updatedAt(), null, policyEvidence(policy));
        }
        IssueRuntimeStageStatus status = switch (action.getStatus()) {
            case COMPLETED -> IssueRuntimeStageStatus.SUCCEEDED;
            case PENDING -> IssueRuntimeStageStatus.PENDING;
            case CLAIMED, EXECUTING -> IssueRuntimeStageStatus.IN_PROGRESS;
            case RETRY_WAITING, EXECUTOR_UNAVAILABLE -> IssueRuntimeStageStatus.FAILED_RETRYABLE;
            case FAILED, CANCELLED, SUPPRESSED -> action.getNextAttemptAt() != null ? IssueRuntimeStageStatus.FAILED_RETRYABLE : IssueRuntimeStageStatus.FAILED_FINAL;
        };
        return stage(IssueRuntimeStageCode.ACTION, status, true, status == IssueRuntimeStageStatus.FAILED_RETRYABLE,
                "ISSUE_ADAPTER_ACTION_" + action.getStatus().name(),
                firstNonBlank(action.getLastError(), "ISSUE_TRACKING AdapterAction status is " + action.getStatus().name() + "."),
                first(action.getUpdatedAt(), action.getCreatedAt()), action.getNextAttemptAt(), List.of(actionEvidence(action)));
    }

    private IssueRuntimeStage executorStage(IssuePolicyDecision policy, AdapterAction action) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) return notRequired(IssueRuntimeStageCode.EXECUTOR, policy.updatedAt());
        if (action == null) return notStarted(IssueRuntimeStageCode.EXECUTOR, "ISSUE_EXECUTOR_WAITING_FOR_ACTION", "Executor waits for the durable ISSUE_TRACKING AdapterAction.");
        AdapterExecutionAuthority authority = executionAuthorityPolicy.authorityFor(AdapterType.ISSUE_TRACKING);
        if (authority != AdapterExecutionAuthority.CORE_GOVERNED) {
            return stage(IssueRuntimeStageCode.EXECUTOR, IssueRuntimeStageStatus.BLOCKED, true, false,
                    AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE,
                    "No Core-governed Issue connector executor is available.", action.getUpdatedAt(), null,
                    List.of(executorEvidence(action, authority)));
        }
        if (!executionAuthorityPolicy.shouldAutoExecuteInCore(AdapterType.ISSUE_TRACKING) && action.getStatus() == AdapterActionStatus.PENDING) {
            return stage(IssueRuntimeStageCode.EXECUTOR, IssueRuntimeStageStatus.BLOCKED, true, false,
                    "ISSUE_EXECUTOR_AUTO_EXECUTION_DISABLED",
                    "Core owns Issue execution but automatic pending-action execution is disabled.", action.getUpdatedAt(), null,
                    List.of(executorEvidence(action, authority)));
        }
        IssueRuntimeStageStatus status = switch (action.getStatus()) {
            case PENDING -> IssueRuntimeStageStatus.PENDING;
            case CLAIMED, EXECUTING -> IssueRuntimeStageStatus.IN_PROGRESS;
            case RETRY_WAITING -> IssueRuntimeStageStatus.FAILED_RETRYABLE;
            case EXECUTOR_UNAVAILABLE -> IssueRuntimeStageStatus.FAILED_RETRYABLE;
            case COMPLETED -> IssueRuntimeStageStatus.SUCCEEDED;
            case FAILED, CANCELLED, SUPPRESSED -> action.getNextAttemptAt() != null ? IssueRuntimeStageStatus.FAILED_RETRYABLE : IssueRuntimeStageStatus.FAILED_FINAL;
        };
        String reason = action.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE
                ? AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE
                : "ISSUE_EXECUTOR_" + action.getStatus().name();
        return stage(IssueRuntimeStageCode.EXECUTOR, status, true, status == IssueRuntimeStageStatus.FAILED_RETRYABLE,
                reason, firstNonBlank(action.getLastError(), "Core-governed Issue executor status is " + action.getStatus().name() + "."),
                first(action.getUpdatedAt(), action.getCreatedAt()), action.getNextAttemptAt(), List.of(executorEvidence(action, authority)));
    }

    private IssueRuntimeStage providerStage(IssuePolicyDecision policy, AdapterAction action, IssueProviderExecutionResult result) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) return notRequired(IssueRuntimeStageCode.PROVIDER, policy.updatedAt());
        if (action == null) return notStarted(IssueRuntimeStageCode.PROVIDER, "ISSUE_PROVIDER_WAITING_FOR_ACTION", "Provider invocation waits for an Issue AdapterAction.");
        if (result != null) {
            return stage(IssueRuntimeStageCode.PROVIDER, IssueRuntimeStageStatus.SUCCEEDED, true, false,
                    "ISSUE_PROVIDER_INVOCATION_OBSERVED",
                    "Provider invocation produced durable execution evidence.", result.getUpdatedAt(), null,
                    List.of(providerEvidence(result)));
        }
        if (action.getStatus() == AdapterActionStatus.EXECUTING || action.getStatus() == AdapterActionStatus.CLAIMED) {
            return stage(IssueRuntimeStageCode.PROVIDER, IssueRuntimeStageStatus.IN_PROGRESS, true, false,
                    "ISSUE_PROVIDER_EXECUTION_IN_PROGRESS", "Issue provider execution is in progress.", action.getUpdatedAt(), null, List.of(actionEvidence(action)));
        }
        if (action.getStatus() == AdapterActionStatus.COMPLETED) {
            return stage(IssueRuntimeStageCode.PROVIDER, IssueRuntimeStageStatus.FAILED_FINAL, true, false,
                    "ISSUE_PROVIDER_EVIDENCE_MISSING_AFTER_COMPLETION",
                    "AdapterAction is completed but no durable Issue provider result exists.", action.getCompletedAt(), null, List.of(actionEvidence(action)));
        }
        if (action.getStatus() == AdapterActionStatus.FAILED || action.getStatus() == AdapterActionStatus.RETRY_WAITING || action.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE) {
            boolean retryable = action.getNextAttemptAt() != null || action.getStatus() != AdapterActionStatus.FAILED;
            return stage(IssueRuntimeStageCode.PROVIDER, retryable ? IssueRuntimeStageStatus.FAILED_RETRYABLE : IssueRuntimeStageStatus.FAILED_FINAL,
                    true, retryable, "ISSUE_PROVIDER_NOT_REACHED",
                    firstNonBlank(action.getLastError(), "Provider execution was not completed."), action.getUpdatedAt(), action.getNextAttemptAt(), List.of(actionEvidence(action)));
        }
        return stage(IssueRuntimeStageCode.PROVIDER, IssueRuntimeStageStatus.NOT_STARTED, true, false,
                "ISSUE_PROVIDER_NOT_REACHED", "Provider execution has not started.", action.getUpdatedAt(), null, List.of(actionEvidence(action)));
    }

    private IssueRuntimeStage resultStage(IssuePolicyDecision policy, AdapterAction action, IssueProviderExecutionResult result) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) return notRequired(IssueRuntimeStageCode.RESULT, policy.updatedAt());
        if (result == null) return notStarted(IssueRuntimeStageCode.RESULT, "ISSUE_PROVIDER_RESULT_NOT_AVAILABLE", "Waiting for durable Issue provider outcome evidence.");
        IssueProviderExecutionDisposition disposition = result.getDisposition();
        if (disposition == IssueProviderExecutionDisposition.CONFIRMED_SUCCESS) {
            return stage(IssueRuntimeStageCode.RESULT, IssueRuntimeStageStatus.SUCCEEDED, true, false,
                    "ISSUE_PROVIDER_CONFIRMED_SUCCESS",
                    "Provider confirmed the Issue operation" + (blank(result.getExternalIssueId()) ? "." : " for external Issue " + result.getExternalIssueId() + "."),
                    result.getUpdatedAt(), null, List.of(providerEvidence(result)));
        }
        if (disposition == IssueProviderExecutionDisposition.UNKNOWN) {
            return stage(IssueRuntimeStageCode.RESULT, IssueRuntimeStageStatus.BLOCKED, true, false,
                    firstNonBlank(result.getFailureCode(), "ISSUE_PROVIDER_OUTCOME_UNKNOWN"),
                    firstNonBlank(result.getErrorMessage(), "Provider outcome is unknown; reconcile before any retry that could repeat the side effect."),
                    result.getUpdatedAt(), null, List.of(providerEvidence(result)));
        }
        boolean retryable = result.isRetryable();
        return stage(IssueRuntimeStageCode.RESULT,
                retryable ? IssueRuntimeStageStatus.FAILED_RETRYABLE : IssueRuntimeStageStatus.FAILED_FINAL,
                true, retryable,
                firstNonBlank(result.getFailureCode(), "ISSUE_PROVIDER_CONFIRMED_FAILURE"),
                firstNonBlank(result.getErrorMessage(), "Provider confirmed the Issue operation failed."),
                result.getUpdatedAt(), retryable ? action == null ? null : action.getNextAttemptAt() : null, List.of(providerEvidence(result)));
    }

    private IssueRuntimeStage linkStage(IssuePolicyDecision policy, AdapterAction action, IssueProviderExecutionResult result, TaskIssueLink link) {
        if (policy != null && policy.decision() == IssuePolicyDecisionOutcome.NOT_REQUIRED) return notRequired(IssueRuntimeStageCode.LINK, policy.updatedAt());
        if (result == null || result.getDisposition() != IssueProviderExecutionDisposition.CONFIRMED_SUCCESS) {
            return notStarted(IssueRuntimeStageCode.LINK, "ISSUE_LINK_WAITING_FOR_CONFIRMED_PROVIDER_SUCCESS", "TaskIssueLink projection waits for confirmed provider success.");
        }
        IssueLinkProjectionStatus projection = result.getProjectionStatus();
        boolean linked = link != null && (TaskIssueLink.LINK_EXTERNAL_CONFIRMED.equalsIgnoreCase(link.getLinkState())
                || !blank(link.getExternalIssueId()) || !blank(link.getIssueId()) || !blank(link.getIssueUrl()));
        boolean synced = link != null && TaskIssueLink.SYNCED.equalsIgnoreCase(link.getSyncStatus());
        if (projection == IssueLinkProjectionStatus.PROJECTED && linked && synced) {
            return stage(IssueRuntimeStageCode.LINK, IssueRuntimeStageStatus.SUCCEEDED, true, false,
                    "TASK_ISSUE_LINK_EXTERNALLY_CONFIRMED", "TaskIssueLink is projected and externally confirmed.",
                    first(link.getUpdatedAt(), result.getProjectedAt(), result.getUpdatedAt()), null,
                    List.of(providerEvidence(result), linkEvidence(link)));
        }
        if (projection == IssueLinkProjectionStatus.FAILED_PERMANENT) {
            return stage(IssueRuntimeStageCode.LINK, IssueRuntimeStageStatus.FAILED_FINAL, true, false,
                    "TASK_ISSUE_LINK_PROJECTION_FAILED_PERMANENT",
                    firstNonBlank(result.getLastProjectionError(), "TaskIssueLink projection exhausted its retry budget."),
                    result.getUpdatedAt(), null, evidence(result, link));
        }
        if (projection == IssueLinkProjectionStatus.RETRY_WAITING) {
            return stage(IssueRuntimeStageCode.LINK, IssueRuntimeStageStatus.FAILED_RETRYABLE, true, true,
                    "TASK_ISSUE_LINK_PROJECTION_RETRY_WAITING",
                    firstNonBlank(result.getLastProjectionError(), "TaskIssueLink projection failed and is scheduled for retry."),
                    result.getUpdatedAt(), result.getNextProjectionAttemptAt(), evidence(result, link));
        }
        if (projection == IssueLinkProjectionStatus.NOT_REQUIRED) {
            return stage(IssueRuntimeStageCode.LINK, IssueRuntimeStageStatus.NOT_REQUIRED, false, false,
                    "TASK_ISSUE_LINK_PROJECTION_NOT_REQUIRED",
                    firstNonBlank(result.getLastProjectionError(), "This provider evidence was superseded and does not require link projection."),
                    result.getUpdatedAt(), null, evidence(result, link));
        }
        if (projection == IssueLinkProjectionStatus.PROJECTED && (!linked || !synced)) {
            return stage(IssueRuntimeStageCode.LINK, IssueRuntimeStageStatus.FAILED_FINAL, true, false,
                    "TASK_ISSUE_LINK_PROJECTION_INCONSISTENT",
                    "Provider evidence is marked PROJECTED but no synchronized external TaskIssueLink can be proven.",
                    first(link == null ? null : link.getUpdatedAt(), result.getUpdatedAt()), null, evidence(result, link));
        }
        return stage(IssueRuntimeStageCode.LINK, IssueRuntimeStageStatus.PENDING, true, false,
                "TASK_ISSUE_LINK_PROJECTION_PENDING",
                "Provider success is durable; TaskIssueLink projection is pending and will not re-run the provider side effect.",
                result.getUpdatedAt(), result.getNextProjectionAttemptAt(), evidence(result, link));
    }

    private List<IssueRuntimeEvidenceRef> evidence(IssueProviderExecutionResult result, TaskIssueLink link) {
        List<IssueRuntimeEvidenceRef> values = new ArrayList<>();
        if (result != null) values.add(providerEvidence(result));
        if (link != null) values.add(linkEvidence(link));
        return values;
    }

    private List<IssueRuntimeEvidenceRef> policyEvidence(IssuePolicyDecision policy) {
        if (policy == null) return List.of();
        return List.of(ref("ISSUE_POLICY_DECISION", policy.decisionId(), "IssuePolicyDecision", policy.updatedAt(), attrs(
                "decision", policy.decision(), "bindingStatus", policy.bindingStatus(), "automationStatus", policy.automationStatus(),
                "connectionId", policy.connectionId(), "projectMappingId", policy.projectMappingId(), "adapterActionId", policy.adapterActionId(),
                "lastErrorCode", policy.lastErrorCode())));
    }

    private IssueRuntimeEvidenceRef actionEvidence(AdapterAction action) {
        return ref("ISSUE_ADAPTER_ACTION", action.getActionId(), "AdapterAction", first(action.getUpdatedAt(), action.getCreatedAt()), attrs(
                "adapterType", action.getAdapterType(), "actionType", action.getActionType(), "status", action.getStatus(),
                "attemptCount", action.getAttemptCount(), "maxAttempts", action.getMaxAttempts(), "executorName", action.getExecutorName(),
                "nextAttemptAt", action.getNextAttemptAt(), "idempotencyKey", action.getIdempotencyKey()));
    }

    private IssueRuntimeEvidenceRef executorEvidence(AdapterAction action, AdapterExecutionAuthority authority) {
        return ref("ISSUE_EXECUTOR_AUTHORITY", action.getActionId(), "AdapterExecutionAuthorityPolicy", first(action.getUpdatedAt(), action.getCreatedAt()), attrs(
                "authority", authority, "autoExecutePending", executionAuthorityPolicy.shouldAutoExecuteInCore(AdapterType.ISSUE_TRACKING),
                "actionStatus", action.getStatus(), "executorName", action.getExecutorName(), "claimedBy", action.getClaimedBy()));
    }

    private IssueRuntimeEvidenceRef providerEvidence(IssueProviderExecutionResult result) {
        return ref("ISSUE_PROVIDER_RESULT", result.getResultId(), "IssueProviderExecutionResult", first(result.getUpdatedAt(), result.getExecutedAt()), attrs(
                "observationKind", result.getObservationKind(), "provider", result.getProvider(), "operation", result.getOperation(),
                "disposition", result.getDisposition(), "providerStatusCode", result.getProviderStatusCode(),
                "providerOutcomeCertainty", result.getProviderOutcomeCertainty(), "failureCode", result.getFailureCode(),
                "externalIssueId", result.getExternalIssueId(), "externalIssueUrl", result.getExternalIssueUrl(),
                "projectionStatus", result.getProjectionStatus(), "projectionAttemptCount", result.getProjectionAttemptCount(),
                "connectionId", result.getConnectionId(), "projectMappingId", result.getProjectMappingId(),
                "technicalPrincipalId", result.getTechnicalPrincipalId(), "credentialId", result.getCredentialId(), "credentialVersion", result.getCredentialVersion(),
                "operationFingerprint", result.getOperationFingerprint(), "responseFingerprint", result.getResponseFingerprint()));
    }

    private IssueRuntimeEvidenceRef linkEvidence(TaskIssueLink link) {
        return ref("TASK_ISSUE_LINK", link.getLinkId(), "TaskIssueLink", link.getUpdatedAt(), attrs(
                "linkState", link.getLinkState(), "syncStatus", link.getSyncStatus(), "issueId", link.getIssueId(),
                "externalIssueId", link.getExternalIssueId(), "issueUrl", link.getIssueUrl(),
                "providerStatusCode", link.getProviderStatusCode(), "providerOutcomeCertainty", link.getProviderOutcomeCertainty(),
                "providerFailureCode", link.getProviderFailureCode()));
    }

    private IssueRuntimeStage notRequired(IssueRuntimeStageCode code, OffsetDateTime at) {
        return stage(code, IssueRuntimeStageStatus.NOT_REQUIRED, false, false, "ISSUE_NOT_REQUIRED",
                "This stage is not required by the effective Issue policy.", at, null, List.of());
    }

    private IssueRuntimeStage notStarted(IssueRuntimeStageCode code, String reason, String summary) {
        return stage(code, IssueRuntimeStageStatus.NOT_STARTED, true, false, reason, summary, null, null, List.of());
    }

    private IssueRuntimeStage stage(IssueRuntimeStageCode code, IssueRuntimeStageStatus status, boolean required, boolean retryable,
            String reason, String summary, OffsetDateTime changedAt, OffsetDateTime retryAfter, List<IssueRuntimeEvidenceRef> evidence) {
        return new IssueRuntimeStage(code, status, required, retryable, reason, summary, changedAt, retryAfter,
                evidence == null ? List.of() : List.copyOf(evidence));
    }

    private String overallStatus(List<IssueRuntimeStage> stages) {
        if (stages.stream().anyMatch(stage -> stage.status() == IssueRuntimeStageStatus.FAILED_FINAL)) return "FAILED_FINAL";
        if (stages.stream().anyMatch(stage -> stage.status() == IssueRuntimeStageStatus.BLOCKED)) return "BLOCKED";
        if (stages.stream().anyMatch(stage -> stage.status() == IssueRuntimeStageStatus.FAILED_RETRYABLE)) return "FAILED_RETRYABLE";
        if (stages.stream().allMatch(stage -> stage.status().isTerminalSuccess())) return "SUCCEEDED";
        if (stages.stream().anyMatch(stage -> stage.status() == IssueRuntimeStageStatus.IN_PROGRESS)) return "IN_PROGRESS";
        return "PENDING";
    }

    private AdapterAction primaryIssueAction(String taskId, IssuePolicyDecision policy) {
        // Once a durable policy decision exists, its adapterActionId is the only current-generation
        // action authority. Falling back to an older task action would mix terminal generations and
        // can make an obsolete provider/link result look current. The fallback exists only for
        // legacy tasks that have no durable policy decision yet.
        if (policy != null) {
            if (blank(policy.adapterActionId())) return null;
            return adapterActions.findById(policy.adapterActionId())
                    .filter(value -> value.getAdapterType() == AdapterType.ISSUE_TRACKING)
                    .orElse(null);
        }
        return adapterActions.findByTaskId(taskId, 100).stream()
                .filter(value -> value != null && value.getAdapterType() == AdapterType.ISSUE_TRACKING)
                .max(Comparator.comparing(value -> first(value.getUpdatedAt(), value.getCreatedAt(), OffsetDateTime.MIN)))
                .orElse(null);
    }

    private TaskIssueLink primaryIssueLink(String tenantId, String taskId, AdapterAction action, IssueProviderExecutionResult providerResult) {
        List<TaskIssueLink> links = taskIssueLinks.findAllByTenantAndTaskId(tenantId, taskId).stream()
                .filter(value -> value != null && (blank(value.getLinkRole()) || "PRIMARY".equalsIgnoreCase(value.getLinkRole())))
                .sorted(Comparator.comparing(TaskIssueLink::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (links.isEmpty()) return null;
        if (action == null) return links.get(0);

        String actionId = action.getActionId();
        Optional<TaskIssueLink> exactAction = links.stream()
                .filter(value -> actionId != null && actionId.equals(value.getIssueActionId()))
                .findFirst();
        if (exactAction.isPresent()) return exactAction.get();

        // Compatibility recovery for pre-lineage links: a provider-confirmed external Issue identity
        // may prove the same link even when issueActionId was not populated by an older writer.
        String externalIssueId = providerResult == null ? null : providerResult.getExternalIssueId();
        if (!blank(externalIssueId)) {
            return links.stream()
                    .filter(value -> externalIssueId.equals(firstNonBlank(value.getExternalIssueId(), value.getIssueId())))
                    .findFirst().orElse(null);
        }
        return null;
    }

    private IssueRuntimeEvidenceRef ref(String type, String id, String authority, OffsetDateTime observedAt, Map<String, String> attributes) {
        return new IssueRuntimeEvidenceRef(type, id, authority, observedAt, Map.copyOf(attributes));
    }

    private Map<String, String> attrs(Object... values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values == null) return result;
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object value = values[i + 1];
            if (values[i] == null || value == null) continue;
            String rendered = String.valueOf(value).trim();
            if (!rendered.isEmpty()) result.put(String.valueOf(values[i]), rendered);
        }
        return result;
    }

    private long revisionFor(Object... values) {
        long hash = 0xcbf29ce484222325L;
        if (values != null) for (Object value : values) {
            String text = String.valueOf(value);
            for (int i = 0; i < text.length(); i++) {
                hash ^= text.charAt(i);
                hash *= 0x100000001b3L;
            }
        }
        return hash == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(hash);
    }

    @SafeVarargs
    private final <T> T first(T... values) {
        if (values == null) return null;
        for (T value : values) if (value != null) return value;
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value.trim();
        return null;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
