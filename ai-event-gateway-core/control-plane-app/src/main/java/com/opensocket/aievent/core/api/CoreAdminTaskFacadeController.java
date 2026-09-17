package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionRepository;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionService;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRepository;
import com.opensocket.aievent.core.callback.CallbackInboxEntry;
import com.opensocket.aievent.core.api.security.ServerActorAuthority;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.callback.CallbackInboxService;
import com.opensocket.aievent.core.callback.CallbackInboxSummary;
import com.opensocket.aievent.core.a2a.A2AParentAggregation;
import com.opensocket.aievent.core.a2a.A2AParentAggregationRepository;
import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.A2ARequestRepository;
import com.opensocket.aievent.core.a2a.A2AResult;
import com.opensocket.aievent.core.a2a.A2AResultProcessing;
import com.opensocket.aievent.core.a2a.A2AResultProcessingRepository;
import com.opensocket.aievent.core.a2a.A2AResultRepository;
import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.assignment.TaskAssignmentService;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.DispatchAttemptLedger;
import com.opensocket.aievent.core.dispatch.DispatchAttemptLedgerService;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestService;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.dispatch.TaskFailureQueueService;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.issue.observability.IssueRuntimeJourneyService;
import com.opensocket.aievent.core.issue.observability.IssueRuntimeJourneyView;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecision;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionRepository;
import com.opensocket.aievent.core.lifecycle.TaskLifecycleService;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceView;
import com.opensocket.aievent.core.task.evidence.TaskRuntimeVerificationView;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStage;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStageCode;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStatus;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyView;
import com.opensocket.aievent.core.task.timeline.TaskCaseTimelineStepView;
import com.opensocket.aievent.core.task.timeline.TaskCaseTimelineView;
import com.opensocket.aievent.core.timeline.AdminFailureQueueResponse;
import com.opensocket.aievent.core.timeline.DispatchTimelineResponse;
import com.opensocket.aievent.core.timeline.DispatchTimelineService;
import com.opensocket.aievent.core.timeline.TaskExecutionJourneyService;
import com.opensocket.aievent.core.timeline.TaskDispatchEvidenceService;
import com.opensocket.aievent.core.timeline.TaskRuntimeVerificationService;

/**
 * Admin UI facade for task and dispatch operations.
 *
 * <p>The public task/dispatch APIs remain under {@code /api/*}. This facade keeps the Admin UI on
 * an explicit {@code /admin/*} contract protected by the Core OPERATOR role and prevents the UI
 * from depending on internal/public action endpoint drift.</p>
 */
@RestController
@RequestMapping("/admin")
public class CoreAdminTaskFacadeController {
    private static final Logger log = LoggerFactory.getLogger(CoreAdminTaskFacadeController.class);

    private final TaskOperationalQuery taskQuery;
    private final TaskLifecycleService taskLifecycleService;
    private final TaskOrchestrationFacade taskOrchestrationFacade;
    private final TaskAssignmentService taskAssignmentService;
    private final ExecutionOperationalQuery executionQuery;
    private final DispatchRequestService dispatchRequestService;
    private final DispatchAttemptHistoryService attemptHistoryService;
    private final DispatchAttemptLedgerService dispatchAttemptLedgerService;
    private final CallbackInboxService callbackInboxService;
    private final TaskFailureQueueService failureQueueService;
    private final DispatchTimelineService timelineService;
    private final TaskExecutionJourneyService executionJourneyService;
    @Autowired(required = false)
    private TaskDispatchEvidenceService taskDispatchEvidenceService;

    @Autowired(required = false)
    private TaskRuntimeVerificationService taskRuntimeVerificationService;

    @Autowired(required = false)
    private IamIdempotencyExecutor taskRemediationIdempotency;

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

    @Autowired(required = false)
    private IssuePolicyDecisionRepository issuePolicyDecisionRepository;

    @Autowired(required = false)
    private IssueRuntimeJourneyService issueRuntimeJourneyService;

    @Autowired(required = false)
    private AdapterActionRepository adapterActionRepository;

    @Autowired(required = false)
    private AdapterActionExecutionService adapterActionExecutionService;

    @Autowired(required = false)
    private AdapterExecutorAuditRepository adapterExecutorAuditRepository;

    @Autowired(required = false)
    private A2ARequestRepository a2aRequestRepository;
    @Autowired(required = false)
    private A2AResultRepository a2aResultRepository;
    @Autowired(required = false)
    private A2AResultProcessingRepository a2aResultProcessingRepository;
    @Autowired(required = false)
    private A2AParentAggregationRepository a2aParentAggregationRepository;

    public CoreAdminTaskFacadeController(
            TaskOperationalQuery taskQuery,
            TaskLifecycleService taskLifecycleService,
            TaskOrchestrationFacade taskOrchestrationFacade,
            TaskAssignmentService taskAssignmentService,
            ExecutionOperationalQuery executionQuery,
            DispatchRequestService dispatchRequestService,
            DispatchAttemptHistoryService attemptHistoryService,
            DispatchAttemptLedgerService dispatchAttemptLedgerService,
            CallbackInboxService callbackInboxService,
            TaskFailureQueueService failureQueueService,
            DispatchTimelineService timelineService,
            TaskExecutionJourneyService executionJourneyService
    ) {
        this.taskQuery = taskQuery;
        this.taskLifecycleService = taskLifecycleService;
        this.taskOrchestrationFacade = taskOrchestrationFacade;
        this.taskAssignmentService = taskAssignmentService;
        this.executionQuery = executionQuery;
        this.dispatchRequestService = dispatchRequestService;
        this.attemptHistoryService = attemptHistoryService;
        this.dispatchAttemptLedgerService = dispatchAttemptLedgerService;
        this.callbackInboxService = callbackInboxService;
        this.failureQueueService = failureQueueService;
        this.timelineService = timelineService;
        this.executionJourneyService = executionJourneyService;
    }

    @GetMapping("/tasks/{taskId}")
    public TaskRecord getTask(@PathVariable String taskId) {
        return taskQuery.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    @GetMapping("/tasks/{taskId}/runtime-view")
    public AdminTaskRuntimeView getTaskRuntimeView(@PathVariable String taskId) {
        TaskRecord task = getTask(taskId);
        List<DispatchRequest> dispatchRequests = executionQuery.findDispatchRequestsByTask(taskId, 100);
        DispatchRequest latestDispatch = dispatchRequests.stream().findFirst().orElse(null);
        TaskIssueLink issueLink = taskIssueLinkRepository.findByTaskId(taskId).orElse(null);
        log.info("task_detail_runtime_view_loaded taskId={} taskStatus={} correlationId={} dispatchRequestId={} dispatchStatus={} agentId={} issueLinked={} dispatchRequestCount={}",
                taskId,
                task.getStatus(),
                task.getCorrelationId(),
                latestDispatch == null ? null : latestDispatch.getDispatchRequestId(),
                latestDispatch == null ? null : latestDispatch.getStatus(),
                latestDispatch == null ? null : latestDispatch.getAgentId(),
                issueLink != null,
                dispatchRequests.size());
        return new AdminTaskRuntimeView(
                task,
                dispatchRequests,
                taskQuery.findRoutingDecisionsByTask(taskId, 1).stream().findFirst().orElse(null),
                issueLink,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }




    /**
     * Phase 6 aggregate read model for Task Detail.
     *
     * <p>This endpoint is a query composition boundary, not a new write authority. It calls application/query
     * services directly (never HTTP fan-out), reports partial section failures independently, and exposes
     * stable section revisions so the Admin UI can poll overview cheaply and refresh only changed sections.</p>
     */
    @GetMapping("/tasks/{taskId}/operations-view")
    @Transactional(readOnly = true)
    public AdminTaskOperationsView taskOperationsView(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "overview,execution,issue,relationships") String include) {
        TaskRecord task = getTask(taskId); // authoritative anti-enumeration/resource boundary
        Set<String> requested = operationsIncludes(include);

        TaskExecutionJourneyView journey = null;
        AdminTaskOperationsSection overview = requested.contains("overview")
                ? operationsSection("overview", task.getVersion(), () -> operationsOverview(task))
                : omittedOperationsSection("overview", task.getVersion());

        AdminTaskOperationsSection execution;
        if (requested.contains("execution")) {
            final TaskExecutionJourneyView loadedJourney;
            try {
                loadedJourney = executionJourneyService.journey(taskId);
                journey = loadedJourney;
                execution = operationsSection("execution", loadedJourney.revision(),
                        () -> operationsExecution(task, loadedJourney));
            } catch (RuntimeException ex) {
                execution = unavailableOperationsSection("execution", task.getVersion(), ex);
            }
        } else {
            execution = omittedOperationsSection("execution", task.getVersion());
        }

        long issueRevision = issueOperationsRevision(task);
        AdminTaskOperationsSection issue = requested.contains("issue")
                ? operationsSection("issue", issueRevision, () -> operationsIssue(task))
                : omittedOperationsSection("issue", issueRevision);

        AdminTaskOperationsSection relationships = requested.contains("relationships")
                ? operationsSection("relationships", task.getVersion(), () -> operationsRelationships(task))
                : omittedOperationsSection("relationships", task.getVersion());

        long snapshotRevision = Math.max(task.getVersion(), Math.max(execution.revision(),
                Math.max(issue.revision(), relationships.revision())));
        return new AdminTaskOperationsView(taskId, task.getTenantId(), task.getCorrelationId(), snapshotRevision,
                OffsetDateTime.now(ZoneOffset.UTC), overview, execution, issue, relationships);
    }

    private AdminTaskOperationsOverview operationsOverview(TaskRecord task) {
        String taskId = task.getTaskId();
        List<DispatchRequest> overviewDispatches = executionQuery.findDispatchRequestsByTask(taskId, 3);
        TaskIssueLink issue = taskIssueLinkRepository.findByTaskId(taskId).orElse(null);
        AdminTaskRuntimeView runtimeView = new AdminTaskRuntimeView(
                task,
                overviewDispatches,
                taskQuery.findRoutingDecisionsByTask(taskId, 1).stream().findFirst().orElse(null),
                issue,
                OffsetDateTime.now(ZoneOffset.UTC));
        DispatchRequest latest = overviewDispatches.stream().findFirst().orElse(null);
        CallbackInboxSummary callbackSummary = callbackInboxService.summarizeTask(taskId, 3);
        long dispatchRevision = latest == null || latest.getUpdatedAt() == null ? 0L : Math.max(0L, latest.getUpdatedAt().toInstant().toEpochMilli());
        long callbackRevision = callbackSummary == null ? 0L
                : (((long) callbackSummary.getTotalCallbacks()) << 32)
                    + Integer.toUnsignedLong(String.valueOf(callbackSummary.getLatestCallbackId()).hashCode());
        long executionRevision = Math.max(task.getVersion(), Math.max(dispatchRevision, callbackRevision));
        long issueRevision = issueOperationsRevision(task);
        TaskRecord parent = task.getParentTaskId() == null ? null
                : taskQuery.findTask(task.getTenantId(), task.getParentTaskId()).orElse(null);
        List<TaskRecord> children = taskQuery.findTaskChildren(task.getTenantId(), taskId, 500);
        long relationshipsRevision = Math.max(task.getVersion(), Math.max(
                parent == null ? 0L : parent.getVersion(),
                children.stream().mapToLong(TaskRecord::getVersion).max().orElse(0L)));
        return new AdminTaskOperationsOverview(runtimeView, task.getVersion(), executionRevision, issueRevision, relationshipsRevision);
    }

    private AdminTaskOperationsExecution operationsExecution(TaskRecord task, TaskExecutionJourneyView journey) {
        String taskId = task.getTaskId();
        List<DispatchRequest> dispatchRequests = executionQuery.findDispatchRequestsByTask(taskId, 100);
        List<DispatchAttemptHistoryRecord> attemptHistory = attemptHistoryService.findByTaskId(taskId, 100);
        List<DispatchAttemptLedger> dispatchLedger = dispatchAttemptLedgerService.findByTaskId(taskId, 100);
        List<CallbackInboxEntry> callbacks = callbackInboxService.findByTaskId(taskId, 100);
        CallbackInboxSummary callbackSummary = callbackInboxService.summarizeTask(taskId, 100);
        DispatchTimelineResponse timeline = timelineService.timeline(taskId, 200);
        TaskCaseTimelineView caseTimeline = taskCaseTimeline(task, journey);
        List<RoutingDecisionRecord> routing = taskQuery.findRoutingDecisionsByTask(taskId, 20);
        TaskDispatchEvidenceView evidence = taskDispatchEvidenceService == null ? null : taskDispatchEvidenceService.evidence(taskId, 200);
        TaskRuntimeVerificationView verification = taskRuntimeVerificationService == null ? null : taskRuntimeVerificationService.verify(taskId, 90, 200);
        return new AdminTaskOperationsExecution(journey, dispatchRequests, attemptHistory, dispatchLedger, callbacks,
                callbackSummary, timeline, caseTimeline, routing, evidence, verification, journey.revision());
    }

    private AdminTaskOperationsIssue operationsIssue(TaskRecord task) {
        String taskId = task.getTaskId();
        TaskIssueLink link = taskIssueLinkRepository.findByTaskId(taskId).orElse(null);
        IssuePolicyDecision decision = issuePolicyDecisionRepository == null ? null
                : issuePolicyDecisionRepository.findByTaskAndPurpose(task.getTenantId(), taskId, "PRIMARY_ISSUE").orElse(null);
        List<AdapterAction> actions = issueAdapterActions(taskId);
        List<AdapterExecutorAuditRecord> providerExecutions = issueProviderExecutions(primaryIssueAction(actions, decision));
        IssueRuntimeJourneyView issueRuntimeJourney = issueRuntimeJourneyService == null ? null : issueRuntimeJourneyService.journey(taskId);
        long revision = issueOperationsRevision(task, link, decision, actions, providerExecutions);
        if (issueRuntimeJourney != null) revision = Math.max(revision, issueRuntimeJourney.revision());
        log.info("task_detail_issue_authority_loaded taskId={} taskIssueSyncPolicy={} taskIssueSyncPolicySource={} issueDecision={} bindingStatus={} automationStatus={} adapterActionCount={} providerExecutionCount={} issueLinked={} issueRuntimeOverall={} issueFirstFailedStage={} issueReasonCode={} issueRevision={}",
                taskId, task.getIssueSyncPolicy(), task.getIssueSyncPolicySource(),
                decision == null ? null : decision.decision(), decision == null ? null : decision.bindingStatus(),
                decision == null ? null : decision.automationStatus(), actions.size(), providerExecutions.size(), link != null,
                issueRuntimeJourney == null ? null : issueRuntimeJourney.overallStatus(),
                issueRuntimeJourney == null ? null : issueRuntimeJourney.firstFailedStage(),
                issueRuntimeJourney == null ? null : issueRuntimeJourney.reasonCode(), revision);
        return new AdminTaskOperationsIssue(link, buildIssueDedupSummary(task, link), decision, actions, providerExecutions, issueRuntimeJourney, revision);
    }

    private long issueOperationsRevision(TaskRecord task) {
        TaskIssueLink link = taskIssueLinkRepository.findByTaskId(task.getTaskId()).orElse(null);
        IssuePolicyDecision decision = issuePolicyDecisionRepository == null ? null
                : issuePolicyDecisionRepository.findByTaskAndPurpose(task.getTenantId(), task.getTaskId(), "PRIMARY_ISSUE").orElse(null);
        List<AdapterAction> actions = issueAdapterActions(task.getTaskId());
        AdapterAction primaryAction = primaryIssueAction(actions, decision);
        List<AdapterExecutorAuditRecord> providerExecutions = issueProviderExecutions(primaryAction);
        return issueOperationsRevision(task, link, decision, actions, providerExecutions);
    }

    private long issueOperationsRevision(TaskRecord task, TaskIssueLink link, IssuePolicyDecision decision,
            List<AdapterAction> actions, List<AdapterExecutorAuditRecord> providerExecutions) {
        long revision = Math.max(0L, task.getVersion());
        if (link != null) revision = Math.max(revision, Math.max(0L, link.getResourceVersion()));
        if (decision != null) {
            revision = Math.max(revision, Math.max(0L, decision.version()));
            revision = Math.max(revision, epochMillis(decision.updatedAt()));
        }
        for (AdapterAction action : actions) {
            revision = Math.max(revision, epochMillis(firstNonNull(action.getUpdatedAt(), action.getCompletedAt(), action.getCreatedAt())));
        }
        for (AdapterExecutorAuditRecord audit : providerExecutions) {
            revision = Math.max(revision, epochMillis(audit.getCreatedAt()));
        }
        if (issueRuntimeJourneyService != null) {
            try {
                revision = Math.max(revision, issueRuntimeJourneyService.journey(task.getTaskId()).revision());
            } catch (RuntimeException ex) {
                log.warn("issue_runtime_journey_revision_unavailable taskId={} errorClass={} safeMessage={}",
                        task.getTaskId(), ex.getClass().getSimpleName(), firstNonBlank(ex.getMessage(), "Issue runtime journey unavailable"));
            }
        }
        return revision;
    }

    private List<AdapterAction> issueAdapterActions(String taskId) {
        if (adapterActionRepository == null) return List.of();
        return adapterActionRepository.findByTaskId(taskId, 100).stream()
                .filter(action -> action != null && action.getAdapterType() == AdapterType.ISSUE_TRACKING)
                .toList();
    }

    private AdapterAction primaryIssueAction(List<AdapterAction> actions, IssuePolicyDecision decision) {
        if (actions == null || actions.isEmpty()) return null;
        if (decision != null && decision.adapterActionId() != null && !decision.adapterActionId().isBlank()) {
            for (AdapterAction action : actions) {
                if (action != null && decision.adapterActionId().equals(action.getActionId())) return action;
            }
        }
        return actions.stream()
                .filter(java.util.Objects::nonNull)
                .max(Comparator.comparing(action -> firstNonNull(action.getUpdatedAt(), action.getCompletedAt(), action.getCreatedAt()),
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<AdapterExecutorAuditRecord> issueProviderExecutions(AdapterAction action) {
        if (adapterExecutorAuditRepository == null || action == null) return List.of();
        return adapterExecutorAuditRepository.findByActionId(action.getActionId(), 100).stream()
                .sorted(Comparator.comparing(AdapterExecutorAuditRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .toList();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) return null;
        for (T value : values) if (value != null) return value;
        return null;
    }

    private static long epochMillis(OffsetDateTime value) {
        return value == null ? 0L : Math.max(0L, value.toInstant().toEpochMilli());
    }

    private AdminTaskOperationsRelationships operationsRelationships(TaskRecord task) {
        String rootTaskId = firstNonBlank(task.getRootTaskId(), task.getTaskId());
        TaskRecord parent = task.getParentTaskId() == null ? null
                : taskQuery.findTask(task.getTenantId(), task.getParentTaskId()).orElse(null);
        List<TaskRecord> children = taskQuery.findTaskChildren(task.getTenantId(), task.getTaskId(), 500);
        AdminTaskA2AEvidence a2a = operationsA2AEvidence(task);
        long revision = Math.max(task.getVersion(), Math.max(
                parent == null ? 0L : parent.getVersion(),
                Math.max(children.stream().mapToLong(TaskRecord::getVersion).max().orElse(0L), a2a.revision())));
        return new AdminTaskOperationsRelationships(rootTaskId, parent, children, children.size(), a2a, revision);
    }

    private AdminTaskA2AEvidence operationsA2AEvidence(TaskRecord task) {
        if (a2aRequestRepository == null) return new AdminTaskA2AEvidence(List.of(), null, List.of(), List.of(), null, 0L);
        String tenantId = task.getTenantId();
        String taskId = task.getTaskId();
        List<A2ARequest> outbound = a2aRequestRepository.findBySourceTask(tenantId, taskId, 200);
        A2ARequest inbound = a2aRequestRepository.findByChildTask(tenantId, taskId).orElse(null);
        List<A2AResult> results = a2aResultRepository == null ? List.of()
                : a2aResultRepository.findByParentTask(tenantId, taskId, 200);
        if (inbound != null && a2aResultRepository != null) {
            A2AResult inboundResult = a2aResultRepository.findByRequest(tenantId, inbound.getRequestId()).orElse(null);
            if (inboundResult != null && results.stream().noneMatch(value -> value.getResultId().equals(inboundResult.getResultId()))) {
                java.util.ArrayList<A2AResult> combined = new java.util.ArrayList<>(results);
                combined.add(inboundResult);
                results = List.copyOf(combined);
            }
        }
        List<A2AResultProcessing> processing = a2aResultProcessingRepository == null ? List.of()
                : a2aResultProcessingRepository.findByTask(tenantId, taskId, 200);
        A2AParentAggregation aggregation = a2aParentAggregationRepository == null ? null
                : a2aParentAggregationRepository.findByParentTask(tenantId, taskId).orElse(null);
        long revision = Math.max(
                outbound.stream().mapToLong(A2ARequest::getVersion).max().orElse(0L),
                Math.max(inbound == null ? 0L : inbound.getVersion(),
                Math.max(results.stream().mapToLong(A2AResult::getVersion).max().orElse(0L),
                Math.max(processing.stream().mapToLong(A2AResultProcessing::getRowVersion).max().orElse(0L),
                        aggregation == null ? 0L : aggregation.getVersion()))));
        return new AdminTaskA2AEvidence(outbound, inbound, results, processing, aggregation, revision);
    }

    private static Set<String> operationsIncludes(String include) {
        Set<String> values = new LinkedHashSet<>();
        if (include != null) {
            for (String value : include.split(",")) {
                String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if (Set.of("overview", "execution", "issue", "relationships").contains(normalized)) values.add(normalized);
            }
        }
        if (values.isEmpty()) values.add("overview");
        return Set.copyOf(values);
    }

    private <T> AdminTaskOperationsSection operationsSection(String name, long revision, Supplier<T> loader) {
        try {
            return new AdminTaskOperationsSection(name, "READY", Math.max(0L, revision), null, null, loader.get());
        } catch (RuntimeException ex) {
            return unavailableOperationsSection(name, revision, ex);
        }
    }

    private AdminTaskOperationsSection unavailableOperationsSection(String name, long revision, RuntimeException ex) {
        String errorCode = "TASK_OPERATIONS_" + name.toUpperCase(Locale.ROOT) + "_UNAVAILABLE";
        log.warn("task_operations_section_unavailable taskIdSection={} errorCode={} errorClass={} safeMessage={}",
                name, errorCode, ex.getClass().getSimpleName(), firstNonBlank(ex.getMessage(), "Section unavailable"));
        return new AdminTaskOperationsSection(name, "UNAVAILABLE", Math.max(0L, revision), errorCode,
                firstNonBlank(ex.getMessage(), "Section unavailable"), null);
    }

    private static AdminTaskOperationsSection omittedOperationsSection(String name, long revision) {
        return new AdminTaskOperationsSection(name, "OMITTED", Math.max(0L, revision), null, null, null);
    }

    /** Canonical evidence-backed operational journey. This is a read model, never a write authority. */
    @GetMapping("/tasks/{taskId}/execution-journey")
    public TaskExecutionJourneyView taskExecutionJourney(@PathVariable String taskId) {
        getTask(taskId); // preserve Task resource authorization/anti-enumeration behavior
        return executionJourneyService.journey(taskId);
    }


    /** Canonical seven-stage Issue Runtime Journey. Read-only observability authority. */
    @GetMapping("/tasks/{taskId}/issue-runtime-journey")
    public IssueRuntimeJourneyView taskIssueRuntimeJourney(@PathVariable String taskId) {
        getTask(taskId); // preserve Task anti-enumeration/resource authorization behavior
        if (issueRuntimeJourneyService == null) {
            throw new IllegalStateException("ISSUE_RUNTIME_JOURNEY_NOT_AVAILABLE");
        }
        return issueRuntimeJourneyService.journey(taskId);
    }

    /**
     * Operator-safe recovery for a failed TaskIssueLink projection. This replays only the local
     * read model from durable provider evidence and cannot execute another provider CREATE.
     */
    @PostMapping("/tasks/{taskId}/issue-link/reconcile")
    public AdminCommandResult<IssueRuntimeJourneyView> reconcileTaskIssueLink(@PathVariable String taskId) {
        getTask(taskId); // preserve Task resource authorization/anti-enumeration behavior
        if (adapterActionExecutionService == null || issueRuntimeJourneyService == null) {
            throw new IllegalStateException("ISSUE_LINK_RECONCILIATION_NOT_AVAILABLE");
        }
        boolean projected = adapterActionExecutionService.reconcileIssueLinkProjectionForTask(taskId);
        IssueRuntimeJourneyView journey = issueRuntimeJourneyService.journey(taskId);
        return AdminCommandResult.success(
                projected ? "TaskIssueLink projection reconciled from durable provider evidence."
                        : "No eligible confirmed provider evidence required TaskIssueLink projection.",
                journey);
    }

    @GetMapping("/tasks/{taskId}/issue-dedup")
    public AdminTaskIssueDedupSummary taskIssueDedup(@PathVariable String taskId) {
        TaskRecord task = getTask(taskId);
        TaskIssueLink link = taskIssueLinkRepository.findByTaskId(taskId).orElse(null);
        return buildIssueDedupSummary(task, link);
    }

    @GetMapping("/tasks/{taskId}/case-timeline")
    public TaskCaseTimelineView taskCaseTimeline(@PathVariable String taskId) {
        TaskRecord task = getTask(taskId);
        return taskCaseTimeline(task, executionJourneyService.journey(taskId));
    }

    private TaskCaseTimelineView taskCaseTimeline(TaskRecord task, TaskExecutionJourneyView journey) {
        String taskId = task.getTaskId();
        String selectedAgentId = executionQuery.findDispatchRequestsByTask(taskId, 1).stream()
                .findFirst()
                .map(DispatchRequest::getAgentId)
                .orElse(null);
        TaskCaseTimelineView view = new TaskCaseTimelineView();
        view.setTaskId(task.getTaskId());
        view.setParentTaskId(task.getParentTaskId());
        view.setCorrelationId(journey.correlationId());
        view.setMatchedFlowId(task.getMatchedFlowId());
        view.setMatchedRuleId(task.getMatchedRuleId());
        view.setRequestedSkill(task.getRequestedSkill());
        view.setEventStage(firstNonBlank(task.getEventStage(), "EXTERNAL"));
        view.setRoutingPath(task.getRoutingPath());
        view.setFailureStage(journey.currentStage() == null ? null : journey.currentStage().name());
        view.setFixAction(journeyFixAction(journey));

        TaskExecutionJourneyStage intake = journeyStage(journey, TaskExecutionJourneyStageCode.INTAKE);
        TaskExecutionJourneyStage routing = journeyStage(journey, TaskExecutionJourneyStageCode.ROUTING);
        TaskExecutionJourneyStage assignment = journeyStage(journey, TaskExecutionJourneyStageCode.ASSIGNMENT);
        TaskExecutionJourneyStage delivery = journeyStage(journey, TaskExecutionJourneyStageCode.DELIVERY);
        TaskExecutionJourneyStage ack = journeyStage(journey, TaskExecutionJourneyStageCode.ACK);
        TaskExecutionJourneyStage result = journeyStage(journey, TaskExecutionJourneyStageCode.RESULT);
        TaskExecutionJourneyStage issuePolicy = journeyStage(journey, TaskExecutionJourneyStageCode.ISSUE_POLICY);
        TaskExecutionJourneyStage issueSync = journeyStage(journey, TaskExecutionJourneyStageCode.PROJECTION_SYNCED);
        String failureStage = journey.currentStage() == null ? null : journey.currentStage().name();
        String fixAction = journeyFixAction(journey);

        view.setSteps(List.of(
                r8CaseTimelineStep(1, "INTAKE_EVENT", firstNonBlank(task.getEventStage(), "EXTERNAL"), task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(intake), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), journeySummary(intake, "Event intake evidence is unavailable."), intake),
                r8CaseTimelineStep(2, "FLOW_RULE_MATCH", "ROUTING", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(routing), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), journeySummary(routing, "Waiting for authoritative routing evidence."), routing),
                r8CaseTimelineStep(3, "SKILL_RESOLUTION", "ROUTING", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(routing), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Capability/rule context is shown for compatibility; canonical pass/block status comes from the ROUTING Journey stage.", routing),
                r8CaseTimelineStep(4, "FLOW_AGENT_ASSIGNMENT", "ASSIGNMENT", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(assignment), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), journeySummary(assignment, "Waiting for authoritative assignment evidence."), assignment),
                r8CaseTimelineStep(5, "RUNTIME_DELIVERY", "DELIVERY", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(delivery), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), journeySummary(delivery, "Waiting for DispatchRequest delivery evidence."), delivery),
                r8CaseTimelineStep(6, "AGENT_ACK", "ACK", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(ack), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), journeySummary(ack, "Waiting for accepted Agent ACK evidence."), ack),
                r8CaseTimelineStep(7, "AGENT_RESULT", "RESULT", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(result), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), journeySummary(result, "Waiting for accepted Agent RESULT/ERROR evidence."), result),
                r8CaseTimelineStep(8, "ISSUE_UPDATE", "ISSUE_SYNC", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, caseStatus(issueSync == null ? issuePolicy : issueSync), failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), issueJourneySummary(issuePolicy, issueSync), issueSync == null ? issuePolicy : issueSync)
        ));
        view.setGeneratedAt(journey.generatedAt());
        return view;
    }

    @GetMapping("/tasks/{taskId}/timeline")
    public DispatchTimelineResponse taskDispatchTimeline(@PathVariable String taskId,
                                                         @RequestParam(defaultValue = "200") int limit) {
        return timelineService.timeline(taskId, safeLimit(limit));
    }

    @GetMapping("/tasks/failure-queue")
    public AdminFailureQueueResponse taskFailureQueue(@RequestParam(defaultValue = "100") int limit) {
        return timelineService.failureQueue(safeLimit(limit));
    }

    @PostMapping("/tasks/{taskId}/manual-retry")
    public AdminCommandResult<TaskRecord> manualRetryTask(@PathVariable String taskId,
                                                          @RequestBody(required = false) AdminReasonRequest request) {
        TaskRecord task = failureQueueService.manualRetry(taskId, reason(request, "Manual retry requested from Admin UI"), OffsetDateTime.now(ZoneOffset.UTC));
        return AdminCommandResult.success("Manual retry requested: " + taskId, task);
    }

    @PostMapping("/tasks/{taskId}/dead-letter")
    public AdminCommandResult<TaskRecord> deadLetterTask(@PathVariable String taskId,
                                                         @RequestBody(required = false) AdminReasonRequest request) {
        TaskRecord task = failureQueueService.deadLetter(taskId, reason(request, "Moved to dead letter from Admin UI"), OffsetDateTime.now(ZoneOffset.UTC));
        return AdminCommandResult.success("Task moved to dead letter: " + taskId, task);
    }

    @PostMapping("/tasks/{taskId}/escalate")
    public AdminCommandResult<TaskRecord> escalateTask(@PathVariable String taskId,
                                                       @RequestBody(required = false) AdminReasonRequest request) {
        TaskRecord task = failureQueueService.escalate(taskId, reason(request, "Escalated from Admin UI"), OffsetDateTime.now(ZoneOffset.UTC));
        return AdminCommandResult.success("Task escalated: " + taskId, task);
    }

    @PostMapping("/tasks/{taskId}/commands")
    public AdminTaskRemediationCommandResult runTaskRemediationCommand(
            @PathVariable String taskId,
            @RequestBody AdminTaskRemediationCommandRequest request) {
        if (request == null || request.commandType() == null || request.commandType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commandType is required");
        }
        if (taskRemediationIdempotency == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DURABLE_IDEMPOTENCY_AUTHORITY_REQUIRED");
        }

        AdminTaskRemediationCommandType commandType = parseCommandType(request.commandType());
        String idempotencyKey = requiredText(request.idempotencyKey(), "idempotencyKey");
        String operatorReason = requiredText(request.reason(), "reason");
        String operatorId = ServerActorAuthority.requireActorId();
        ServerActorAuthority.rejectSpoofedActor(request.operatorId(), operatorId);

        TaskRecord before = getTask(taskId);
        verifyExpectedTaskVersion(before, request.expectedTaskVersion());
        String tenantId = requiredText(before.getTenantId(), "task.tenantId");
        AdminTaskRemediationIdempotencyRequest canonicalRequest = new AdminTaskRemediationIdempotencyRequest(
                taskId,
                commandType.name(),
                request.expectedTaskVersion(),
                operatorReason,
                request.payload() == null ? Map.of() : request.payload());

        IamIdempotencyExecutor.ExecutionResult<AdminTaskRemediationCommandResult> execution =
                taskRemediationIdempotency.executeWithState(
                        tenantId,
                        operatorId,
                        "ADMIN_TASK_REMEDIATION:" + commandType.name(),
                        idempotencyKey,
                        canonicalRequest,
                        200,
                        AdminTaskRemediationCommandResult.class,
                        () -> executeTaskRemediationCommand(
                                taskId,
                                commandType,
                                request.expectedTaskVersion(),
                                idempotencyKey,
                                operatorReason,
                                operatorId,
                                request.payload()));
        return execution.replay() ? execution.value().asReplay() : execution.value();
    }

    private AdminTaskRemediationCommandResult executeTaskRemediationCommand(
            String taskId,
            AdminTaskRemediationCommandType commandType,
            Long expectedTaskVersion,
            String idempotencyKey,
            String operatorReason,
            String operatorId,
            Map<String, Object> payload) {
        TaskRecord before = getTask(taskId);
        verifyExpectedTaskVersion(before, expectedTaskVersion);
        List<String> allowed = allowedTaskRemediationCommands(before);
        AdminTaskCommandTaskSnapshot beforeSnapshot = taskSnapshot(before);
        if (!allowed.contains(commandType.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Command " + commandType.name() + " is not allowed for task status " + statusName(before)
                            + "; allowed=" + allowed);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String commandReason = "TASK_REMEDIATION_COMMAND " + commandType.name()
                + "; operator=" + operatorId
                + "; idempotencyKey=" + idempotencyKey
                + "; reason=" + operatorReason;
        Object effect = null;
        switch (commandType) {
            case REEVALUATE_ROUTING -> effect = taskLifecycleService.reassign(taskId, commandReason);
            case ASSIGN_AGENT -> {
                String targetAgentId = payloadText(payload, "targetAgentId", "agentId");
                if (targetAgentId == null || targetAgentId.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload.targetAgentId is required for ASSIGN_AGENT");
                }
                AssignmentDecisionResult assignment = taskAssignmentService.assignToSpecificAgent(before, targetAgentId, commandReason + "; MANUAL_OVERRIDE targetAgentId=" + targetAgentId);
                if (!assignment.assignmentCreated() && assignment.assignmentId() == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, firstNonBlank(assignment.reason(), "Target agent assignment was not accepted"));
                }
                effect = assignment;
            }
            case CHANGE_POOL -> {
                String targetPoolId = payloadText(payload, "targetPoolId", "poolId");
                if (targetPoolId == null || targetPoolId.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload.targetPoolId is required for CHANGE_POOL");
                }
                before.setTargetPoolId(targetPoolId);
                before.setAssignedPoolId(targetPoolId);
                before.setStatus(TaskStatus.QUEUED);
                before.setNextDispatchAttemptAt(null);
                before.setDispatchRetryReason(null);
                before.setUpdatedAt(now);
                before.setLifecycleReason(commandReason + "; MANUAL_OVERRIDE targetPoolId=" + targetPoolId);
                taskOrchestrationFacade.saveExecutionState(before);
                effect = taskLifecycleService.reassign(taskId, commandReason + "; CHANGE_POOL targetPoolId=" + targetPoolId);
            }
            case MOVE_TO_MANUAL_QUEUE -> {
                before.setStatus(TaskStatus.RETRY_WAIT);
                before.setNextDispatchAttemptAt(null);
                before.setDispatchRetryReason("MANUAL_ASSIGNMENT_REQUIRED:" + operatorReason);
                before.setDispatchRecoveryClaimedBy(null);
                before.setDispatchRecoveryClaimUntil(null);
                before.setUpdatedAt(now);
                before.setLifecycleReason(commandReason + "; MANUAL_ASSIGNMENT_REQUIRED");
                effect = taskOrchestrationFacade.saveExecutionState(before);
            }
            case RETRY_DELIVERY -> {
                DispatchRequest dispatch = latestDispatchForTask(taskId);
                effect = dispatchRequestService.retry(dispatch.getDispatchRequestId(), commandReason, false, true);
            }
            case RETRY_TASK -> effect = failureQueueService.manualRetry(taskId, commandReason, now);
            case CANCEL_TASK -> effect = taskLifecycleService.cancel(taskId, commandReason);
            case IGNORE_TASK -> effect = failureQueueService.deadLetter(taskId, commandReason + "; ignoredByOperator=true", now);
        }

        TaskRecord after = getTask(taskId);
        AdminTaskCommandAudit audit = new AdminTaskCommandAudit(
                operatorId,
                now.toString(),
                commandType.name(),
                operatorReason,
                beforeSnapshot,
                taskSnapshot(after),
                idempotencyKey,
                expectedTaskVersion,
                taskVersion(after),
                "Original automatic routing evidence is preserved; remediation writes lifecycle/effect evidence only.");
        return new AdminTaskRemediationCommandResult(
                true,
                "Task remediation command accepted: " + commandType.name(),
                now.toString(),
                taskId,
                commandType.name(),
                allowedTaskRemediationCommands(after),
                audit,
                after,
                effect,
                false);
    }

    @GetMapping("/tasks/{taskId}/dispatch-requests")
    public List<DispatchRequest> taskDispatchRequests(@PathVariable String taskId,
                                                       @RequestParam(defaultValue = "100") int limit) {
        return executionQuery.findDispatchRequestsByTask(taskId, safeLimit(limit));
    }

    @GetMapping("/tasks/{taskId}/dispatch-attempt-history")
    public List<DispatchAttemptHistoryRecord> taskDispatchAttemptHistory(@PathVariable String taskId,
                                                                         @RequestParam(defaultValue = "100") int limit) {
        return attemptHistoryService.findByTaskId(taskId, safeLimit(limit));
    }

    @GetMapping("/tasks/{taskId}/dispatch-ledger")
    public List<DispatchAttemptLedger> taskDispatchLedger(@PathVariable String taskId,
                                                          @RequestParam(defaultValue = "100") int limit) {
        return dispatchAttemptLedgerService.findByTaskId(taskId, safeLimit(limit));
    }

    @GetMapping("/tasks/{taskId}/callback-inbox")
    public List<CallbackInboxEntry> taskCallbackInbox(@PathVariable String taskId,
                                                      @RequestParam(defaultValue = "100") int limit) {
        return callbackInboxService.findByTaskId(taskId, safeLimit(limit));
    }

    @GetMapping("/tasks/{taskId}/callback-inbox/summary")
    public CallbackInboxSummary taskCallbackInboxSummary(@PathVariable String taskId,
                                                         @RequestParam(defaultValue = "100") int limit) {
        return callbackInboxService.summarizeTask(taskId, safeLimit(limit));
    }

    @GetMapping("/tasks/{taskId}/routing-decisions")
    public List<RoutingDecisionRecord> taskRoutingDecisions(@PathVariable String taskId,
                                                            @RequestParam(defaultValue = "20") int limit) {
        return taskQuery.findRoutingDecisionsByTask(taskId, safeLimit(limit));
    }

    @GetMapping("/dispatch-attempt-history")
    public List<DispatchAttemptHistoryRecord> recentDispatchAttemptHistory(@RequestParam(defaultValue = "100") int limit) {
        return attemptHistoryService.recent(safeLimit(limit));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public AdminCommandResult<TaskRecord> cancelTask(@PathVariable String taskId,
                                                     @RequestBody(required = false) AdminReasonRequest request) {
        TaskRecord task = taskLifecycleService.cancel(taskId, reason(request, "Cancelled from Admin UI"));
        return AdminCommandResult.success("Task cancelled: " + taskId, task);
    }

    @PostMapping("/tasks/{taskId}/reassign")
    public AdminCommandResult<TaskRecord> reassignTask(@PathVariable String taskId,
                                                       @RequestBody(required = false) AdminReasonRequest request) {
        TaskRecord task = taskLifecycleService.reassign(taskId, reason(request, "Reassigned from Admin UI"));
        return AdminCommandResult.success("Task reassignment requested: " + taskId, task);
    }

    @PostMapping("/tasks/{taskId}/timeout")
    public AdminCommandResult<TaskRecord> timeoutTask(@PathVariable String taskId,
                                                      @RequestBody(required = false) AdminReasonRequest request) {
        TaskRecord task = taskLifecycleService.timeout(taskId, reason(request, "Timed out from Admin UI"));
        return AdminCommandResult.success("Task timed out: " + taskId, task);
    }

    @PostMapping("/tasks/{taskId}/retry")
    public AdminCommandResult<DispatchRequest> retryTaskLatestDispatch(@PathVariable String taskId,
                                                                       @RequestBody(required = false) AdminRetryRequest request) {
        TaskRecord task = getTask(taskId);
        if (task.getStatus() == TaskStatus.WAITING_HUMAN || task.getStatus() == TaskStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task retry is blocked while the task is held for Human/Security review.");
        }
        DispatchRequest dispatch = latestDispatchForTask(taskId);
        DispatchRequest retried = dispatchRequestService.retry(
                dispatch.getDispatchRequestId(),
                reason(request, "Retry requested from Admin UI for task " + taskId),
                request != null && Boolean.TRUE.equals(request.resetAttempts()),
                request == null || request.immediate() == null || Boolean.TRUE.equals(request.immediate())
        );
        return AdminCommandResult.success("Dispatch retry requested for task: " + taskId, retried);
    }

    @GetMapping("/dispatch-requests")
    public List<DispatchRequest> recentDispatchRequests(@RequestParam(defaultValue = "100") int limit) {
        return executionQuery.recentDispatchRequests(safeLimit(limit));
    }

    @GetMapping("/dispatch-requests/{dispatchRequestId}")
    public DispatchRequest getDispatchRequest(@PathVariable String dispatchRequestId) {
        return executionQuery.findDispatchRequest(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
    }

    @GetMapping("/dispatch-requests/{dispatchRequestId}/ledger")
    public DispatchAttemptLedger getDispatchRequestLedger(@PathVariable String dispatchRequestId,
                                                          @RequestParam(defaultValue = "100") int limit) {
        return dispatchAttemptLedgerService.findByDispatchRequestId(dispatchRequestId, safeLimit(limit))
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request ledger not found: " + dispatchRequestId));
    }


    @GetMapping("/dispatch-requests/{dispatchRequestId}/callback-inbox")
    public List<CallbackInboxEntry> dispatchRequestCallbackInbox(@PathVariable String dispatchRequestId,
                                                                 @RequestParam(defaultValue = "100") int limit) {
        return callbackInboxService.findByDispatchRequestId(dispatchRequestId, safeLimit(limit));
    }

    @GetMapping("/dispatch-requests/{dispatchRequestId}/callback-inbox/summary")
    public CallbackInboxSummary dispatchRequestCallbackInboxSummary(@PathVariable String dispatchRequestId,
                                                                    @RequestParam(defaultValue = "100") int limit) {
        return callbackInboxService.summarizeDispatchRequest(dispatchRequestId, safeLimit(limit));
    }

    @GetMapping("/callbacks/inbox/recent")
    public List<CallbackInboxEntry> recentCallbackInbox(@RequestParam(defaultValue = "100") int limit) {
        return callbackInboxService.recent(safeLimit(limit));
    }

    @PostMapping("/dispatch-requests/{dispatchRequestId}/retry")
    public AdminCommandResult<DispatchRequest> retryDispatchRequest(@PathVariable String dispatchRequestId,
                                                                    @RequestBody(required = false) AdminRetryRequest request) {
        DispatchRequest retried = dispatchRequestService.retry(
                dispatchRequestId,
                reason(request, "Retry requested from Admin UI"),
                request != null && Boolean.TRUE.equals(request.resetAttempts()),
                request == null || request.immediate() == null || Boolean.TRUE.equals(request.immediate())
        );
        return AdminCommandResult.success("Dispatch retry requested: " + dispatchRequestId, retried);
    }

    @PostMapping("/dispatch-requests/{dispatchRequestId}/cancel")
    public AdminCommandResult<DispatchRequest> cancelDispatchRequest(@PathVariable String dispatchRequestId,
                                                                    @RequestBody(required = false) AdminReasonRequest request) {
        DispatchRequest cancelled = dispatchRequestService.cancel(dispatchRequestId, reason(request, "Cancelled from Admin UI"));
        return AdminCommandResult.success("Dispatch request cancelled: " + dispatchRequestId, cancelled);
    }

    private AdminTaskRemediationCommandType parseCommandType(String raw) {
        try {
            return AdminTaskRemediationCommandType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown task remediation commandType: " + raw);
        }
    }

    private void verifyExpectedTaskVersion(TaskRecord task, Long expectedTaskVersion) {
        if (expectedTaskVersion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "expectedTaskVersion is required for Task remediation commands");
        }
        long currentVersion = taskVersion(task);
        if (currentVersion != expectedTaskVersion.longValue()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "RESOURCE_VERSION_CONFLICT currentVersion=" + currentVersion + " expectedVersion=" + expectedTaskVersion);
        }
    }

    private long taskVersion(TaskRecord task) {
        return task == null ? 0L : task.getVersion();
    }

    private List<String> allowedTaskRemediationCommands(TaskRecord task) {
        if (task == null || task.getStatus() == null) {
            return List.of("REEVALUATE_ROUTING", "CANCEL_TASK");
        }
        TaskStatus status = task.getStatus().canonical();
        String retryReason = task.getDispatchRetryReason() == null ? "" : task.getDispatchRetryReason().toUpperCase(Locale.ROOT);
        if (task.getStatus().isSucceeded() || task.getStatus() == TaskStatus.DEAD_LETTER || task.getStatus() == TaskStatus.CANCELLED) {
            return List.of();
        }
        if (retryReason.startsWith("MANUAL_ASSIGNMENT_REQUIRED")) {
            return List.of("ASSIGN_AGENT", "CHANGE_POOL", "CANCEL_TASK");
        }
        return switch (status) {
            case QUEUED, RETRY_WAIT, ORPHANED, RECONCILING -> List.of(
                    "REEVALUATE_ROUTING", "ASSIGN_AGENT", "CHANGE_POOL", "MOVE_TO_MANUAL_QUEUE", "CANCEL_TASK");
            case ASSIGNED, RUNNING -> List.of("RETRY_DELIVERY", "CANCEL_TASK");
            case FAILED, ESCALATED -> List.of("RETRY_TASK", "ASSIGN_AGENT", "CHANGE_POOL", "IGNORE_TASK", "CANCEL_TASK");
            default -> List.of("REEVALUATE_ROUTING", "CANCEL_TASK");
        };
    }

    private AdminTaskCommandTaskSnapshot taskSnapshot(TaskRecord task) {
        return new AdminTaskCommandTaskSnapshot(
                task == null ? null : task.getTaskId(),
                statusName(task),
                task == null ? null : task.getMatchedFlowId(),
                task == null ? null : task.getMatchedRuleId(),
                task == null ? null : firstNonBlank(task.getTargetPoolId(), task.getAssignedPoolId()),
                task == null ? null : task.getLifecycleReason(),
                task == null ? null : task.getDispatchRetryReason(),
                task == null ? null : stringAt(task.getUpdatedAt()),
                taskVersion(task)
        );
    }


    private AdminTaskIssueDedupSummary buildIssueDedupSummary(TaskRecord task, TaskIssueLink link) {
        String taskId = task == null ? null : task.getTaskId();
        String issueType = issueTypeFor(task);
        String issueScope = firstNonBlank(
                task == null ? null : task.getTargetPoolId(),
                task == null ? null : task.getAssignedPoolId(),
                task == null ? null : task.getMatchedFlowId(),
                taskId);
        boolean recoverable = isRecoverableIssueType(issueType);
        boolean terminalSuccess = task != null && task.getStatus() != null && task.getStatus().isSucceeded();
        String activeStatus = "NO_ACTIVE_BLOCKER".equals(issueType)
                ? "NOT_REQUIRED"
                : terminalSuccess && recoverable
                        ? "AUTO_RESOLVED"
                        : recoverable ? "ACTIVE" : "REVIEW_REQUIRED";
        long occurrenceCount = Math.max(1L, task == null ? 1L : task.getOccurrenceCountAtCreation());
        String activeIssueKey = String.join(":", List.of(
                firstNonBlank(taskId, "unknown-task"),
                issueType,
                firstNonBlank(issueScope, "task"),
                activeStatus));
        return new AdminTaskIssueDedupSummary(
                activeIssueKey,
                issueType,
                issueScope,
                activeStatus,
                link == null ? null : link.getSyncStatus(),
                link == null ? null : link.getIssueId(),
                link == null ? null : link.getIssueUrl(),
                occurrenceCount,
                stringAt(link == null ? null : link.getCreatedAt()),
                stringAt(link == null ? null : link.getUpdatedAt()),
                recoverable ? "RECOVERABLE_AUTO_RESOLVE_ON_COMPLETION" : "GOVERNANCE_REVIEW_REQUIRED",
                !recoverable && !"NOT_REQUIRED".equals(activeStatus),
                "taskId + issueType + issueScope + activeStatus",
                "Update occurrenceCount and lastOccurredAt; do not create duplicate active Issue.",
                terminalSuccess && recoverable
                        ? "Task succeeded; recoverable Issue is eligible for automatic resolution."
                        : "Active issue dedup keeps one open issue per task/blocker/scope/status."
        );
    }

    private String issueTypeFor(TaskRecord task) {
        if (task == null || task.getStatus() == null || task.getStatus().isSucceeded()) return "NO_ACTIVE_BLOCKER";
        String reason = firstNonBlank(task.getDispatchRetryReason(), task.getLifecycleReason(), task.getCreatedReason(), "").toUpperCase(Locale.ROOT);
        if (reason.contains("MANUAL_ASSIGNMENT_REQUIRED")) return "MANUAL_ACTION_REQUIRED";
        if (reason.contains("DELIVERY") || reason.contains("ACK")) return "DELIVERY_BLOCKED";
        if (reason.contains("CALLBACK") || reason.contains("RESULT")) return "EXECUTION_BLOCKED";
        if (reason.contains("RUNTIME") || reason.contains("OFFLINE") || reason.contains("CAPACITY") || reason.contains("BACKOFF")) return "RUNTIME_BLOCKED";
        if (task.getStatus().isFailed()) return "EXECUTION_BLOCKED";
        return "CONFIGURATION_BLOCKED";
    }

    private boolean isRecoverableIssueType(String issueType) {
        return "RUNTIME_BLOCKED".equals(issueType)
                || "DELIVERY_BLOCKED".equals(issueType)
                || "EXECUTION_BLOCKED".equals(issueType);
    }

    private String statusName(TaskRecord task) {
        return task == null || task.getStatus() == null ? null : task.getStatus().name();
    }

    private String stringAt(OffsetDateTime at) {
        return at == null ? null : at.toString();
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private String payloadText(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private DispatchRequest latestDispatchForTask(String taskId) {
        return executionQuery.findDispatchRequestsByTask(taskId, 100).stream()
                .max(Comparator.comparing(this::dispatchSortTime))
                .orElseThrow(() -> new IllegalArgumentException("No dispatch request found for task: " + taskId));
    }

    private OffsetDateTime dispatchSortTime(DispatchRequest request) {
        if (request.getUpdatedAt() != null) return request.getUpdatedAt();
        if (request.getCreatedAt() != null) return request.getCreatedAt();
        return OffsetDateTime.MIN;
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }

    private String reason(AdminReasonRequest request, String fallback) {
        if (request != null && request.reason() != null && !request.reason().isBlank()) return request.reason().trim();
        return fallback;
    }

    private String reason(AdminRetryRequest request, String fallback) {
        if (request != null && request.reason() != null && !request.reason().isBlank()) return request.reason().trim();
        return fallback;
    }

    private static TaskCaseTimelineStepView r8CaseTimelineStep(int sequence, String stepCode, String eventStage, String eventType, String sourceSystem, String targetSystem, String matchedFlowId, String matchedRuleId, String requestedSkill, String routingPath, String selectedAgentId, String status, String failureStage, String fixAction, String taskId, String parentTaskId, String correlationId, String message, TaskExecutionJourneyStage journeyStage) {
        TaskCaseTimelineStepView step = new TaskCaseTimelineStepView();
        step.setSequence(sequence);
        step.setStepCode(stepCode);
        step.setEventStage(eventStage);
        step.setEventType(eventType);
        step.setSourceSystem(sourceSystem);
        step.setTargetSystem(targetSystem);
        step.setMatchedFlowId(matchedFlowId);
        step.setMatchedRuleId(matchedRuleId);
        step.setRequestedSkill(requestedSkill);
        step.setRoutingPath(routingPath);
        step.setSelectedAgentId(selectedAgentId);
        step.setStatus(status);
        step.setFailureStage(failureStage);
        step.setFixAction(fixAction);
        step.setTaskId(taskId);
        step.setParentTaskId(parentTaskId);
        step.setCorrelationId(correlationId);
        step.setMessage(message);
        step.setOccurredAt(journeyStage == null ? null : firstNonNull(journeyStage.completedAt(), journeyStage.lastChangedAt(), journeyStage.startedAt()));
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("authority", "V24_TASK_EXECUTION_JOURNEY_V1");
        if (journeyStage != null) {
            details.put("journeyStage", journeyStage.stage().name());
            details.put("journeyStatus", journeyStage.status().name());
            details.put("reasonCode", journeyStage.reasonCode());
            details.put("retryability", journeyStage.retryability().name());
            details.put("retryAfter", journeyStage.retryAfter());
            details.put("revision", journeyStage.revision());
            details.put("evidenceRefs", journeyStage.evidenceRefs());
        }
        step.setDetails(details);
        return step;
    }

    private TaskExecutionJourneyStage journeyStage(TaskExecutionJourneyView journey, TaskExecutionJourneyStageCode code) {
        if (journey == null || journey.stages() == null) return null;
        return journey.stages().stream().filter(stage -> stage.stage() == code).findFirst().orElse(null);
    }

    private String caseStatus(TaskExecutionJourneyStage stage) {
        if (stage == null || stage.status() == null) return "PENDING";
        return switch (stage.status()) {
            case SUCCEEDED, NOT_APPLICABLE -> "PASS";
            case BLOCKED, FAILED_RETRYABLE, FAILED_FINAL, CONFLICT -> "BLOCKED";
            case NOT_STARTED, PENDING, IN_PROGRESS -> "PENDING";
        };
    }

    private String journeySummary(TaskExecutionJourneyStage stage, String fallback) {
        return stage == null ? fallback : firstNonBlank(stage.summary(), fallback);
    }

    private String issueJourneySummary(TaskExecutionJourneyStage policy, TaskExecutionJourneyStage sync) {
        if (policy != null && policy.status() == TaskExecutionJourneyStatus.SUCCEEDED && !policy.required()) {
            return journeySummary(policy, "Issue policy determined that no external Issue is required.");
        }
        if (sync != null) return journeySummary(sync, "Waiting for canonical Issue projection synchronization evidence.");
        return journeySummary(policy, "Waiting for Issue policy evaluation.");
    }

    private String journeyFixAction(TaskExecutionJourneyView journey) {
        if (journey == null || journey.currentStage() == null) return null;
        return switch (journey.currentStage()) {
            case ROUTING -> "Run Task-level Readiness";
            case ASSIGNMENT -> "Review Agent assignment and capacity";
            case DELIVERY, ACK, RESULT -> "Open Agent Diagnostics";
            case ISSUE_POLICY -> "Review Issue Policy";
            case ISSUE_INTENT, ISSUE_MATERIALIZATION -> "Review Issue Projection";
            case OUTBOX, PROVIDER, READBACK, PROJECTION_SYNCED -> "Open Issue Reliability / Reconciliation";
            case INTAKE -> "Review Event Intake evidence";
        };
    }

    private static String p4FailureStage(TaskRecord task, String selectedAgentId) {
        if (task.getMatchedFlowId() == null || task.getMatchedRuleId() == null || !"FLOW_RULE".equalsIgnoreCase(firstNonBlank(task.getRoutingPath(), ""))) return "NO_ACTIVE_FLOW_RULE";
        boolean capabilityRequired = hasRequiredCapabilities(task);
        if (capabilityRequired && !hasResolvedCapabilityRequirement(task)) return "REQUIRED_CAPABILITY_MISSING";
        if (selectedAgentId == null || selectedAgentId.isBlank()) return "NO_ELIGIBLE_AGENT";
        return null;
    }

    private static String p4FixAction(TaskRecord task, String selectedAgentId) {
        String failureStage = p4FailureStage(task, selectedAgentId);
        if ("NO_ACTIVE_FLOW_RULE".equals(failureStage)) return "Create or activate a matching Dispatch Flow Rule and select at least one approved Agent.";
        if ("REQUIRED_CAPABILITY_MISSING".equals(failureStage)) return "Select the required Capability in the Dispatch Flow or approve an Agent that provides it.";
        if ("NO_ELIGIBLE_AGENT".equals(failureStage)) return "Check the Flow-selected Agents for approval, Capability, Runtime connection, and available capacity.";
        return "Review the formal Task timeline, then check Netty Delivery, Agent ACK, and RESULT callback.";
    }

    private static boolean hasRequiredCapabilities(TaskRecord task) {
        return task != null
                && task.getRequiredCapabilities() != null
                && !task.getRequiredCapabilities().isEmpty();
    }

    private static boolean hasResolvedCapabilityRequirement(TaskRecord task) {
        return !hasRequiredCapabilities(task)
                || task.getRequiredCapabilities().stream()
                        .allMatch(value -> value != null && !value.isBlank());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    public record AdminTaskOperationsView(
            String taskId, String tenantId, String correlationId, long snapshotRevision, OffsetDateTime generatedAt,
            AdminTaskOperationsSection overview, AdminTaskOperationsSection execution,
            AdminTaskOperationsSection issue, AdminTaskOperationsSection relationships) {}

    public record AdminTaskOperationsSection(
            String name, String status, long revision, String errorCode, String errorMessage, Object payload) {}

    public record AdminTaskOperationsOverview(
            AdminTaskRuntimeView runtimeView, long revision, long executionRevision, long issueRevision, long relationshipsRevision) {}

    public record AdminTaskOperationsExecution(
            TaskExecutionJourneyView journey, List<DispatchRequest> dispatchRequests,
            List<DispatchAttemptHistoryRecord> attemptHistory, List<DispatchAttemptLedger> dispatchLedger,
            List<CallbackInboxEntry> callbackInbox, CallbackInboxSummary callbackInboxSummary,
            DispatchTimelineResponse timeline, TaskCaseTimelineView caseTimeline,
            List<RoutingDecisionRecord> routingDecisions, TaskDispatchEvidenceView dispatchEvidence,
            TaskRuntimeVerificationView runtimeVerification, long revision) {}

    public record AdminTaskOperationsIssue(
            TaskIssueLink issueTracking, AdminTaskIssueDedupSummary issueDedup, IssuePolicyDecision issuePolicyDecision,
            List<AdapterAction> adapterActions, List<AdapterExecutorAuditRecord> providerExecutions,
            IssueRuntimeJourneyView issueRuntimeJourney, long revision) {}

    public record AdminTaskA2AEvidence(
            List<A2ARequest> outboundRequests, A2ARequest inboundRequest, List<A2AResult> results,
            List<A2AResultProcessing> resultProcessing, A2AParentAggregation parentAggregation, long revision) {}

    public record AdminTaskOperationsRelationships(
            String rootTaskId, TaskRecord parentTask, List<TaskRecord> childTasks, int directChildCount,
            AdminTaskA2AEvidence a2a, long revision) {}

    public record AdminReasonRequest(String reason) {}
    public record AdminRetryRequest(String reason, Boolean resetAttempts, Boolean immediate) {}
    public record AdminTaskRuntimeView(TaskRecord task, List<DispatchRequest> dispatchRequests, RoutingDecisionRecord latestRoutingDecision, TaskIssueLink issueTracking, OffsetDateTime generatedAt) {}

    public enum AdminTaskRemediationCommandType {
        REEVALUATE_ROUTING,
        ASSIGN_AGENT,
        CHANGE_POOL,
        MOVE_TO_MANUAL_QUEUE,
        RETRY_DELIVERY,
        RETRY_TASK,
        CANCEL_TASK,
        IGNORE_TASK
    }

    public record AdminTaskRemediationIdempotencyRequest(
            String taskId,
            String commandType,
            Long expectedTaskVersion,
            String reason,
            Map<String, Object> payload) {}

    public record AdminTaskRemediationCommandRequest(
            String commandType,
            Long expectedTaskVersion,
            String idempotencyKey,
            String reason,
            String operatorId,
            Map<String, Object> payload) {}

    public record AdminTaskCommandTaskSnapshot(
            String taskId,
            String status,
            String matchedFlowId,
            String matchedRuleId,
            String targetPoolId,
            String lifecycleReason,
            String dispatchRetryReason,
            String updatedAt,
            long version) {}

    public record AdminTaskCommandAudit(
            String operatorId,
            String timestamp,
            String commandType,
            String reason,
            AdminTaskCommandTaskSnapshot beforeState,
            AdminTaskCommandTaskSnapshot afterState,
            String idempotencyKey,
            Long expectedTaskVersion,
            long resultingTaskVersion,
            String evidencePolicy) {}


    public record AdminTaskIssueDedupSummary(
            String activeIssueKey,
            String issueType,
            String issueScope,
            String activeStatus,
            String syncStatus,
            String externalIssueId,
            String externalIssueUrl,
            long occurrenceCount,
            String firstOccurredAt,
            String lastOccurredAt,
            String autoResolutionPolicy,
            boolean governanceReviewRequired,
            String dedupRule,
            String repeatedOccurrenceBehavior,
            String summary) {}

    public record AdminTaskRemediationCommandResult(
            boolean success,
            String message,
            String timestamp,
            String taskId,
            String commandType,
            List<String> allowedCommandsAfter,
            AdminTaskCommandAudit audit,
            TaskRecord task,
            Object effect,
            boolean idempotentReplay) {
        AdminTaskRemediationCommandResult asReplay() {
            return new AdminTaskRemediationCommandResult(
                    success, message, OffsetDateTime.now(ZoneOffset.UTC).toString(), taskId, commandType,
                    allowedCommandsAfter, audit, task, effect, true);
        }
    }

    public record AdminCommandResult<T>(boolean success, String message, String timestamp, T payload) {
        static <T> AdminCommandResult<T> success(String message, T payload) {
            return new AdminCommandResult<>(true, message, OffsetDateTime.now(ZoneOffset.UTC).toString(), payload);
        }
    }
}
