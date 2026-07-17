package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.callback.CallbackInboxEntry;
import com.opensocket.aievent.core.callback.CallbackInboxService;
import com.opensocket.aievent.core.callback.CallbackInboxSummary;
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
import com.opensocket.aievent.core.task.TaskRecord;
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
    private final ExecutionOperationalQuery executionQuery;
    private final DispatchRequestService dispatchRequestService;
    private final DispatchAttemptHistoryService attemptHistoryService;
    private final DispatchAttemptLedgerService dispatchAttemptLedgerService;
    private final CallbackInboxService callbackInboxService;
    private final TaskFailureQueueService failureQueueService;
    private final DispatchTimelineService timelineService;

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

    public CoreAdminTaskFacadeController(
            TaskOperationalQuery taskQuery,
            TaskLifecycleService taskLifecycleService,
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

    public record AdminCommandResult<T>(boolean success, String message, String timestamp, T payload) {
        static <T> AdminCommandResult<T> success(String message, T payload) {
            return new AdminCommandResult<>(true, message, OffsetDateTime.now(ZoneOffset.UTC).toString(), payload);
        }
    }
}
