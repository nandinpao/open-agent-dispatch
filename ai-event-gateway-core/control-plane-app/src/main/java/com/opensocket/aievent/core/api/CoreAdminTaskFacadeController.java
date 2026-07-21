package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

import com.opensocket.aievent.core.callback.CallbackInboxEntry;
import com.opensocket.aievent.core.callback.CallbackInboxService;
import com.opensocket.aievent.core.callback.CallbackInboxSummary;
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
import com.opensocket.aievent.core.lifecycle.TaskLifecycleService;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.timeline.TaskCaseTimelineStepView;
import com.opensocket.aievent.core.task.timeline.TaskCaseTimelineView;
import com.opensocket.aievent.core.timeline.AdminFailureQueueResponse;
import com.opensocket.aievent.core.timeline.DispatchTimelineResponse;
import com.opensocket.aievent.core.timeline.DispatchTimelineService;

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
    private final ConcurrentMap<String, AdminTaskRemediationCommandResult> taskRemediationIdempotencyCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

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
            DispatchTimelineService timelineService
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
    }

    @GetMapping("/tasks/{taskId}")
    public TaskRecord getTask(@PathVariable String taskId) {
        return taskQuery.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    @GetMapping("/tasks/{taskId}/runtime-view")
    public AdminTaskRuntimeView getTaskRuntimeView(@PathVariable String taskId) {
        TaskRecord task = getTask(taskId);
        return new AdminTaskRuntimeView(
                task,
                executionQuery.findDispatchRequestsByTask(taskId, 100),
                taskQuery.findRoutingDecisionsByTask(taskId, 1).stream().findFirst().orElse(null),
                taskIssueLinkRepository.findByTaskId(taskId).orElse(null),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
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
        String selectedAgentId = executionQuery.findDispatchRequestsByTask(taskId, 1).stream()
                .findFirst()
                .map(DispatchRequest::getAgentId)
                .orElse(null);
        TaskCaseTimelineView view = new TaskCaseTimelineView();
        view.setTaskId(task.getTaskId());
        view.setParentTaskId(task.getParentTaskId());
        view.setCorrelationId(firstNonBlank(task.getCorrelationId(), task.getIncidentId(), task.getTaskId()));
        view.setMatchedFlowId(task.getMatchedFlowId());
        view.setMatchedRuleId(task.getMatchedRuleId());
        view.setRequestedSkill(task.getRequestedSkill());
        view.setEventStage(firstNonBlank(task.getEventStage(), "EXTERNAL"));
        view.setRoutingPath(task.getRoutingPath());
        view.setFailureStage(p4FailureStage(task, selectedAgentId));
        view.setFixAction(p4FixAction(task, selectedAgentId));
        String failureStage = p4FailureStage(task, selectedAgentId);
        String fixAction = p4FixAction(task, selectedAgentId);
        boolean flowReady = task.getMatchedFlowId() != null && task.getMatchedRuleId() != null && "FLOW_RULE".equalsIgnoreCase(firstNonBlank(task.getRoutingPath(), ""));
        boolean capabilityRequired = hasRequiredCapabilities(task);
        boolean skillReady = !capabilityRequired || hasResolvedCapabilityRequirement(task);
        boolean agentReady = selectedAgentId != null && !selectedAgentId.isBlank();
        boolean taskCompleted = task.getStatus() != null && "COMPLETED".equalsIgnoreCase(task.getStatus().name());
        view.setSteps(List.of(
                r8CaseTimelineStep(1, "INTAKE_EVENT", firstNonBlank(task.getEventStage(), "EXTERNAL"), task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, "PASS", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Event accepted by Core; Flow repair starts from eventStage/source/eventType evidence."),
                r8CaseTimelineStep(2, "FLOW_RULE_MATCH", "FLOW_RULE_MATCH", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, flowReady ? "PASS" : "BLOCKED", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Formal routing requires matchedFlowId, matchedRuleId, and routingPath=FLOW_RULE."),
                r8CaseTimelineStep(3, "SKILL_RESOLUTION", "SKILL_RESOLUTION", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, skillReady ? "PASS" : flowReady ? "BLOCKED" : "PENDING", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Capability is optional; TaskRecord.requiredCapabilities is the persisted task-level requirement evidence."),
                r8CaseTimelineStep(4, "FLOW_AGENT_ASSIGNMENT", "FLOW_AGENT_ASSIGNMENT", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, agentReady ? "PASS" : skillReady ? "BLOCKED" : "PENDING", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Agent must be assigned from flow_agent_assignments on the matched Dispatch Flow."),
                r8CaseTimelineStep(5, "RUNTIME_DELIVERY", "RUNTIME_DELIVERY", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, agentReady ? "PASS" : "PENDING", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Dispatch Request / runtime delivery is checked only after Flow evidence is complete."),
                r8CaseTimelineStep(6, "AGENT_ACK", "AGENT_ACK", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, taskCompleted ? "PASS" : "PENDING", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Agent ACK is expected after gateway delivery."),
                r8CaseTimelineStep(7, "AGENT_RESULT", "AGENT_RESULT", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, taskCompleted ? "PASS" : "PENDING", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "Agent RESULT callback closes the task loop."),
                r8CaseTimelineStep(8, "ISSUE_UPDATE", "ISSUE_UPDATE", task.getEventType(), task.getSourceSystem(), task.getTargetSystem(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getRequestedSkill(), task.getRoutingPath(), selectedAgentId, "PENDING", failureStage, fixAction, task.getTaskId(), task.getParentTaskId(), view.getCorrelationId(), "If an Issue policy exists, Issue sync is handled after Agent RESULT.")
        ));
        view.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
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
        AdminTaskRemediationCommandType commandType = parseCommandType(request.commandType());
        String idempotencyKey = requiredText(request.idempotencyKey(), "idempotencyKey");
        String operatorReason = requiredText(request.reason(), "reason");
        String operatorId = firstNonBlank(request.operatorId(), "admin-ui");
        String cacheKey = taskId + ":" + commandType.name() + ":" + idempotencyKey;
        AdminTaskRemediationCommandResult cached = taskRemediationIdempotencyCache.get(cacheKey);
        if (cached != null) {
            return cached.asReplay();
        }

        TaskRecord before = getTask(taskId);
        verifyExpectedTaskVersion(before, request.expectedTaskVersion());
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
                String targetAgentId = payloadText(request.payload(), "targetAgentId", "agentId");
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
                String targetPoolId = payloadText(request.payload(), "targetPoolId", "poolId");
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
                request.expectedTaskVersion(),
                taskVersion(after),
                "Original automatic routing evidence is preserved; remediation writes lifecycle/effect evidence only."
        );
        AdminTaskRemediationCommandResult result = new AdminTaskRemediationCommandResult(
                true,
                "Task remediation command accepted: " + commandType.name(),
                now.toString(),
                taskId,
                commandType.name(),
                allowedTaskRemediationCommands(after),
                audit,
                after,
                effect,
                false
        );
        taskRemediationIdempotencyCache.putIfAbsent(cacheKey, result);
        return result;
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
            return;
        }
        long currentVersion = taskVersion(task);
        if (currentVersion != expectedTaskVersion.longValue()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "RESOURCE_VERSION_CONFLICT currentVersion=" + currentVersion + " expectedVersion=" + expectedTaskVersion);
        }
    }

    private long taskVersion(TaskRecord task) {
        if (task == null || task.getUpdatedAt() == null) {
            return 0L;
        }
        return task.getUpdatedAt().toInstant().toEpochMilli();
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

    private static TaskCaseTimelineStepView r8CaseTimelineStep(int sequence, String stepCode, String eventStage, String eventType, String sourceSystem, String targetSystem, String matchedFlowId, String matchedRuleId, String requestedSkill, String routingPath, String selectedAgentId, String status, String failureStage, String fixAction, String taskId, String parentTaskId, String correlationId, String message) {
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
        step.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        step.setDetails(Map.of(
                "P4_TASK_DETAIL_FLOW_REPAIR_CENTER", true,
                "formalSuccessRequires", List.of("matchedFlowId", "matchedRuleId", "routingPath=FLOW_RULE", "selectedAgentId"),
                "capabilityRequirement", "TaskRecord.requiredCapabilities is the persisted task-level requirement evidence"
        ));
        return step;
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
