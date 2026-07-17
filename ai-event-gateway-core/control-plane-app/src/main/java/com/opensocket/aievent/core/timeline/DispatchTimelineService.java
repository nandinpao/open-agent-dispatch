package com.opensocket.aievent.core.timeline;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskDispatchAttempt;
import com.opensocket.aievent.core.assignment.TaskDispatchAttemptRepository;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttempt;
import com.opensocket.aievent.core.executionattempt.TaskExecutionAttemptRepository;
import com.opensocket.aievent.core.routing.DispatchUserFacingError;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

/**
 * TODO 15-E read model that merges task, dispatch, assignment, execution attempt,
 * callback, retry and DLQ records into one ordered timeline for Admin UI and audit usage.
 */
@Service
public class DispatchTimelineService {
    private static final Logger log = LoggerFactory.getLogger(DispatchTimelineService.class);
    private static final List<TaskStatus> FAILURE_QUEUE_STATUSES = List.of(
            TaskStatus.RETRY_WAIT,
            TaskStatus.FAILED,
            TaskStatus.ESCALATED,
            TaskStatus.DEAD_LETTER,
            TaskStatus.ORPHANED,
            TaskStatus.RECONCILING
    );

    private final TaskOperationalQuery taskQuery;
    private final ExecutionOperationalQuery executionQuery;
    private final DispatchAttemptHistoryService attemptHistoryService;
    private final TaskDispatchAttemptRepository dispatchAttemptRepository;
    private final TaskExecutionAttemptRepository executionAttemptRepository;

    public DispatchTimelineService(TaskOperationalQuery taskQuery,
                                   ExecutionOperationalQuery executionQuery,
                                   DispatchAttemptHistoryService attemptHistoryService,
                                   TaskDispatchAttemptRepository dispatchAttemptRepository,
                                   TaskExecutionAttemptRepository executionAttemptRepository) {
        this.taskQuery = taskQuery;
        this.executionQuery = executionQuery;
        this.attemptHistoryService = attemptHistoryService;
        this.dispatchAttemptRepository = dispatchAttemptRepository;
        this.executionAttemptRepository = executionAttemptRepository;
    }

    public DispatchTimelineResponse timeline(String taskId, int limit) {
        TaskRecord task = taskQuery.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        int safeLimit = safeLimit(limit);
        List<DispatchTimelineEvent> events = buildTimelineEvents(task, safeLimit);
        return new DispatchTimelineResponse(taskId, task, now(), countByStage(events), events);
    }

    public AdminFailureQueueResponse failureQueue(int limit) {
        int safeLimit = safeLimit(limit);
        List<TaskRecord> records = FAILURE_QUEUE_STATUSES.stream()
                .flatMap(status -> searchByStatus(status, safeLimit).stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(TaskRecord::getTaskId, task -> task, this::newerTask, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(this::taskSortTime).reversed())
                .limit(safeLimit)
                .toList();

        List<AdminFailureQueueItem> items = records.stream()
                .map(task -> failureItem(task, 80))
                .toList();
        Map<String, Integer> statuses = countFailureStatuses(items);
        Map<String, Integer> categories = countFailureReasonCategories(items);
        Map<String, Integer> dispatchErrors = countDispatchErrors(items);
        log.info("task_failure_queue_loaded limit={} returned={} statuses={} categories={} dispatchErrors={}", safeLimit, items.size(), statuses, categories, dispatchErrors);
        items.stream().limit(50).forEach(item -> log.warn(
                "task_failure_queue_item taskId={} status={} reasonCategory={} blockedReason={} failureReason={} dispatchWaitReason={} lifecycleReason={} dispatchRetryReason={} nextDispatchAttemptAt={} terminalAt={} latestStage={} latestAction={} latestStatus={} latestSeverity={}",
                item.taskId(),
                item.status(),
                item.reasonCategory(),
                item.blockedReason(),
                item.failureReason(),
                item.dispatchWaitReason(),
                item.lifecycleReason(),
                item.dispatchRetryReason(),
                item.nextDispatchAttemptAt(),
                item.terminalAt(),
                item.latestTimelineEvent() == null ? null : item.latestTimelineEvent().stage(),
                item.latestTimelineEvent() == null ? null : item.latestTimelineEvent().action(),
                item.latestTimelineEvent() == null ? null : item.latestTimelineEvent().status(),
                item.latestTimelineEvent() == null ? null : item.latestTimelineEvent().severity()
        ));
        return new AdminFailureQueueResponse(now(), items.size(), statuses, categories, dispatchErrors, items);
    }

    private List<TaskRecord> searchByStatus(TaskStatus status, int limit) {
        TaskQuery query = new TaskQuery();
        query.setStatus(status);
        query.setLimit(limit);
        return taskQuery.searchTasks(query);
    }

    private AdminFailureQueueItem failureItem(TaskRecord task, int timelineLimit) {
        List<DispatchTimelineEvent> events = buildTimelineEvents(task, timelineLimit);
        DispatchTimelineEvent latest = events.isEmpty() ? null : events.get(events.size() - 1);
        RoutingDecisionRecord latestRoutingDecision = latestRoutingDecision(task.getTaskId());
        DispatchUserFacingError userFacingError = latestRoutingDecision == null ? null : latestRoutingDecision.getUserFacingError();
        String reasonCategory = failureReasonCategory(task, userFacingError);
        return new AdminFailureQueueItem(
                task.getTaskId(),
                task.getIncidentId(),
                task.getTaskType(),
                task.getStatus(),
                task.getPriority(),
                task.getTenantId(),
                task.getSiteId(),
                task.getPlantId(),
                task.getObjectType(),
                task.getObjectId(),
                task.getErrorCode(),
                reasonCategory,
                blockedReason(task, userFacingError),
                failureReason(task),
                dispatchWaitReason(task, userFacingError),
                task.getLifecycleReason(),
                task.getDispatchRetryReason(),
                task.getDispatchAttemptCount(),
                task.getNextDispatchAttemptAt(),
                task.getTerminalAt(),
                task.getUpdatedAt(),
                latestRoutingDecision,
                userFacingError,
                latest,
                actionHints(task)
        );
    }

    private List<DispatchTimelineEvent> buildTimelineEvents(TaskRecord task, int limit) {
        List<DispatchTimelineEvent> events = new ArrayList<>();
        addTaskEvents(events, task);
        addDispatchAttempts(events, task.getTaskId(), limit);
        addAssignments(events, task.getTaskId(), limit);
        addDispatchRequests(events, task.getTaskId(), limit);
        addExecutionAttempts(events, task.getTaskId(), limit);
        addCallbacks(events, task.getTaskId(), limit);
        addAttemptHistory(events, task.getTaskId(), limit);
        return sequence(events, limit);
    }

    private void addTaskEvents(List<DispatchTimelineEvent> events, TaskRecord task) {
        events.add(event(0, task.getCreatedAt(), "TASK", "TASK_CREATED", status(task.getStatus()), "INFO", "task",
                "Task created and queued for orchestration",
                refs("taskId", task.getTaskId(), "incidentId", task.getIncidentId()),
                details("taskType", task.getTaskType(), "priority", task.getPriority(), "requiredCapabilities", task.getRequiredCapabilities(),
                        "createdReason", task.getCreatedReason(), "occurrenceCountAtCreation", task.getOccurrenceCountAtCreation())));

        if (task.getNextDispatchAttemptAt() != null) {
            events.add(event(0, task.getUpdatedAt(), "RETRY", "TASK_RETRY_WAIT", status(task.getStatus()), "WARN", "task",
                    "Task is waiting for a future dispatch attempt",
                    refs("taskId", task.getTaskId()),
                    details("nextDispatchAttemptAt", task.getNextDispatchAttemptAt(), "dispatchAttemptCount", task.getDispatchAttemptCount(),
                            "dispatchRetryReason", task.getDispatchRetryReason())));
        }
        if (task.getStatus() != null && task.getStatus().isTerminal()) {
            events.add(event(0, first(task.getTerminalAt(), task.getUpdatedAt()), "TASK", terminalAction(task.getStatus()), status(task.getStatus()), terminalSeverity(task.getStatus()), "task",
                    firstNonBlank(task.getLifecycleReason(), "Task reached terminal state"),
                    refs("taskId", task.getTaskId(), "incidentId", task.getIncidentId()),
                    details("terminalAt", task.getTerminalAt(), "errorCode", task.getErrorCode())));
        } else if (task.getStatus() == TaskStatus.ORPHANED || task.getStatus() == TaskStatus.RECONCILING) {
            events.add(event(0, task.getUpdatedAt(), "RECONCILE", task.getStatus().name(), task.getStatus().name(), "WARN", "task",
                    firstNonBlank(task.getLifecycleReason(), "Task requires reconciliation"),
                    refs("taskId", task.getTaskId()),
                    details("dispatchRecoveryClaimedBy", task.getDispatchRecoveryClaimedBy(), "dispatchRecoveryClaimUntil", task.getDispatchRecoveryClaimUntil())));
        }
    }

    private void addDispatchAttempts(List<DispatchTimelineEvent> events, String taskId, int limit) {
        for (TaskDispatchAttempt attempt : dispatchAttemptRepository.findByTaskId(taskId, limit)) {
            events.add(event(0, first(attempt.getCreatedAt(), attempt.getUpdatedAt()), "DISPATCH", "DISPATCH_ATTEMPT_CREATED", status(attempt.getStatus()), "INFO", "dispatch-attempt",
                    firstNonBlank(attempt.getDecisionReason(), "Dispatch attempt created"),
                    refs("taskId", taskId, "dispatchAttemptId", attempt.getDispatchAttemptId(), "routingDecisionId", attempt.getRoutingDecisionId(), "agentId", attempt.getSelectedAgentId()),
                    details("selectedGatewayNodeId", attempt.getSelectedGatewayNodeId(), "selectedAgentSessionId", attempt.getSelectedAgentSessionId(),
                            "selectedScore", attempt.getSelectedScore(), "eligibilityStatus", attempt.getEligibilityStatus(),
                            "scoreBreakdown", attempt.getScoreBreakdown(), "eligibilityFacts", attempt.getEligibilityFacts())));
            if (attempt.getEligibilityStatus() != null) {
                events.add(event(0, attempt.getUpdatedAt(), "ELIGIBILITY", "ELIGIBILITY_EVALUATED", attempt.getEligibilityStatus().name(),
                        "ELIGIBLE".equals(attempt.getEligibilityStatus().name()) ? "INFO" : "WARN", "dispatch-attempt",
                        "Dispatch eligibility evaluated",
                        refs("taskId", taskId, "dispatchAttemptId", attempt.getDispatchAttemptId(), "agentId", attempt.getSelectedAgentId()),
                        details("status", attempt.getStatus(), "eligibilityFacts", attempt.getEligibilityFacts())));
            }
        }
    }

    private void addAssignments(List<DispatchTimelineEvent> events, String taskId, int limit) {
        for (TaskAssignment assignment : taskQuery.findAssignmentsByTask(taskId, limit)) {
            events.add(event(0, assignment.getCreatedAt(), "ASSIGNMENT", "ASSIGNMENT_CREATED", status(assignment.getStatus()), "INFO", "assignment",
                    firstNonBlank(assignment.getReason(), "Task assigned to Agent"),
                    refs("taskId", taskId, "assignmentId", assignment.getAssignmentId(),
                            "dispatchAttemptId", assignment.getDispatchAttemptId(), "agentId", assignment.getAgentId()),
                    details("ownerGatewayNodeId", assignment.getOwnerGatewayNodeId(), "agentSessionId", assignment.getAgentSessionId(),
                            "leaseId", assignment.getLeaseId(), "leaseExpiresAt", assignment.getLeaseExpiresAt(), "fencingTokenPresent", assignment.getFencingToken() != null,
                            "capacityReserved", assignment.isCapacityReserved(), "score", assignment.getScore())));
            if (assignment.getLeaseId() != null || assignment.getFencingToken() != null) {
                events.add(event(0, assignment.getCreatedAt(), "LEASE", "LEASE_GRANTED", status(assignment.getStatus()), "INFO", "assignment",
                        "Assignment lease and fencing token granted",
                        refs("taskId", taskId, "assignmentId", assignment.getAssignmentId(), "leaseId", assignment.getLeaseId()),
                        details("leaseExpiresAt", assignment.getLeaseExpiresAt(), "fencingTokenPresent", assignment.getFencingToken() != null)));
            }
        }
    }

    private void addDispatchRequests(List<DispatchTimelineEvent> events, String taskId, int limit) {
        for (DispatchRequest request : executionQuery.findDispatchRequestsByTask(taskId, limit)) {
            events.add(event(0, request.getCreatedAt(), "DISPATCH", "DISPATCH_REQUEST_CREATED", status(request.getStatus()), "INFO", "dispatch-request",
                    firstNonBlank(request.getReason(), "Dispatch request created"),
                    refs("taskId", taskId, "dispatchRequestId", request.getDispatchRequestId(), "assignmentId", request.getAssignmentId(), "agentId", request.getAgentId()),
                    details("reviewMode", request.getReviewMode(), "eligibilityStatus", request.getEligibilityStatus(), "dispatchMethod", request.getDispatchMethod(),
                            "attemptCount", request.getAttemptCount(), "ownerGatewayNodeId", request.getOwnerGatewayNodeId(), "agentSessionId", request.getAgentSessionId())));
            if (request.getDispatchedAt() != null) {
                events.add(event(0, request.getDispatchedAt(), "DISPATCH", "COMMAND_DISPATCHED", status(request.getStatus()), "INFO", "dispatch-request",
                        "Command dispatched to Gateway / Agent",
                        refs("taskId", taskId, "dispatchRequestId", request.getDispatchRequestId(), "assignmentId", request.getAssignmentId(), "agentId", request.getAgentId()),
                        details("gatewayDispatchPath", request.getGatewayDispatchPath(), "dispatchTokenPresent", request.getDispatchToken() != null)));
            }
            if (request.getRetryWaitingAt() != null || request.getNextRetryAt() != null) {
                events.add(event(0, first(request.getRetryWaitingAt(), request.getUpdatedAt()), "RETRY", "DISPATCH_RETRY_WAIT", status(request.getStatus()), "WARN", "dispatch-request",
                        firstNonBlank(request.getLastError(), "Dispatch retry wait scheduled"),
                        refs("taskId", taskId, "dispatchRequestId", request.getDispatchRequestId(), "agentId", request.getAgentId()),
                        details("nextRetryAt", request.getNextRetryAt(), "attemptCount", request.getAttemptCount())));
            }
            if (request.getFailedAt() != null) {
                events.add(event(0, request.getFailedAt(), "DISPATCH", "DISPATCH_FAILED", status(request.getStatus()), "ERROR", "dispatch-request",
                        firstNonBlank(request.getLastError(), "Dispatch failed"),
                        refs("taskId", taskId, "dispatchRequestId", request.getDispatchRequestId(), "agentId", request.getAgentId()),
                        details("attemptCount", request.getAttemptCount())));
            }
            if (request.getDeadLetterAt() != null) {
                events.add(event(0, request.getDeadLetterAt(), "DLQ", "DISPATCH_DEAD_LETTERED", status(request.getStatus()), "ERROR", "dispatch-request",
                        firstNonBlank(request.getLastError(), "Dispatch moved to dead letter"),
                        refs("taskId", taskId, "dispatchRequestId", request.getDispatchRequestId()),
                        details("attemptCount", request.getAttemptCount())));
            }
        }
    }

    private void addExecutionAttempts(List<DispatchTimelineEvent> events, String taskId, int limit) {
        for (TaskExecutionAttempt attempt : executionAttemptRepository.findByTaskId(taskId, limit)) {
            events.add(event(0, attempt.getCreatedAt(), "EXECUTION", "EXECUTION_ATTEMPT_CREATED", status(attempt.getStatus()), "INFO", "execution-attempt",
                    "Execution attempt created",
                    refs("taskId", taskId, "executionAttemptId", attempt.getExecutionAttemptId(), "assignmentId", attempt.getAssignmentId(), "agentId", attempt.getAgentId()),
                    details("attemptNo", attempt.getAttemptNo(), "dispatchAttemptId", attempt.getDispatchAttemptId(),
                            "leaseId", attempt.getLeaseId(), "fencingTokenPresent", attempt.getFencingToken() != null)));
            if (attempt.getStartedAt() != null) {
                events.add(event(0, attempt.getStartedAt(), "EXECUTION", "EXECUTION_STARTED", status(attempt.getStatus()), "INFO", "execution-attempt",
                        "Agent execution started",
                        refs("taskId", taskId, "executionAttemptId", attempt.getExecutionAttemptId(), "assignmentId", attempt.getAssignmentId()),
                        details("attemptNo", attempt.getAttemptNo())));
            }
            if (attempt.getCompletedAt() != null) {
                events.add(event(0, attempt.getCompletedAt(), "EXECUTION", executionCompletionAction(attempt), status(attempt.getStatus()),
                        executionCompletionSeverity(attempt), "execution-attempt",
                        firstNonBlank(attempt.getErrorMessage(), attempt.getResultCode(), "Execution attempt completed"),
                        refs("taskId", taskId, "executionAttemptId", attempt.getExecutionAttemptId(), "callbackId", attempt.getCallbackId()),
                        details("resultCode", attempt.getResultCode(), "errorCode", attempt.getErrorCode(), "errorMessage", attempt.getErrorMessage())));
            }
        }
    }

    private void addCallbacks(List<DispatchTimelineEvent> events, String taskId, int limit) {
        for (TaskCallbackRecord callback : executionQuery.findCallbacksByTask(taskId, limit)) {
            String action = callback.isDuplicate()
                    ? "CALLBACK_IGNORED_DUPLICATE"
                    : callback.isAccepted() ? "CALLBACK_RECEIVED" : "CALLBACK_REJECTED";
            String severity = callback.isAccepted() ? "INFO" : "WARN";
            events.add(event(0, first(callback.getProcessedAt(), callback.getOccurredAt()), "CALLBACK", action,
                    callback.getCallbackType() == null ? null : callback.getCallbackType().name(), severity, "callback",
                    firstNonBlank(callback.getIgnoredReason(), callback.getMessage(), callback.getErrorMessage(), "Task callback processed"),
                    refs("taskId", taskId, "callbackId", callback.getCallbackId(), "dispatchRequestId", callback.getDispatchRequestId(),
                            "assignmentId", callback.getAssignmentId(), "agentId", callback.getAgentId()),
                    details("attemptNo", callback.getAttemptNo(), "fencingTokenPresent", callback.getFencingToken() != null,
                            "progressPercent", callback.getProgressPercent(), "errorCode", callback.getErrorCode(),
                            "previousTaskStatus", callback.getPreviousTaskStatus(), "newTaskStatus", callback.getNewTaskStatus(),
                            "previousDispatchStatus", callback.getPreviousDispatchStatus(), "newDispatchStatus", callback.getNewDispatchStatus())));
        }
    }

    private void addAttemptHistory(List<DispatchTimelineEvent> events, String taskId, int limit) {
        for (DispatchAttemptHistoryRecord history : attemptHistoryService.findByTaskId(taskId, limit)) {
            events.add(event(0, history.getCreatedAt(), "AUDIT", "DISPATCH_AUDIT_EVENT", history.getStatus(),
                    auditSeverity(history), "dispatch-attempt-history",
                    firstNonBlank(history.getReason(), history.getErrorMessage(), history.getStatus()),
                    refs("taskId", taskId, "dispatchRequestId", history.getDispatchRequestId(), "assignmentId", history.getAssignmentId(),
                            "agentId", history.getAgentId()),
                    details("attemptNo", history.getAttemptNo(), "eventType", history.getEventType(), "errorCode", history.getErrorCode(),
                            "ownerGatewayNodeId", history.getOwnerGatewayNodeId(), "agentSessionId", history.getAgentSessionId(),
                            "payloadJsonPresent", history.getPayloadJson() != null)));
        }
    }

    private List<DispatchTimelineEvent> sequence(List<DispatchTimelineEvent> events, int limit) {
        List<DispatchTimelineEvent> sorted = events.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::eventSortTime).thenComparing(DispatchTimelineEvent::stage).thenComparing(DispatchTimelineEvent::action))
                .limit(safeLimit(limit))
                .toList();
        List<DispatchTimelineEvent> sequenced = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            DispatchTimelineEvent e = sorted.get(i);
            sequenced.add(new DispatchTimelineEvent(i + 1, e.occurredAt(), e.stage(), e.action(), e.status(), e.severity(), e.source(), e.message(), e.references(), e.details()));
        }
        return sequenced;
    }

    private Map<String, Integer> countByStage(List<DispatchTimelineEvent> events) {
        return events.stream()
                .collect(Collectors.groupingBy(DispatchTimelineEvent::stage, LinkedHashMap::new, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private Map<String, Integer> countFailureStatuses(List<AdminFailureQueueItem> items) {
        return items.stream()
                .collect(Collectors.groupingBy(item -> item.status() == null ? "UNKNOWN" : item.status().name(), LinkedHashMap::new, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private Map<String, Integer> countFailureReasonCategories(List<AdminFailureQueueItem> items) {
        return items.stream()
                .collect(Collectors.groupingBy(item -> item.reasonCategory() == null || item.reasonCategory().isBlank() ? "UNKNOWN" : item.reasonCategory(), LinkedHashMap::new, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private Map<String, Integer> countDispatchErrors(List<AdminFailureQueueItem> items) {
        return items.stream()
                .filter(item -> item.userFacingDispatchError() != null && item.userFacingDispatchError().getCode() != null)
                .collect(Collectors.groupingBy(item -> item.userFacingDispatchError().getCode().name(), LinkedHashMap::new, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private RoutingDecisionRecord latestRoutingDecision(String taskId) {
        if (taskId == null || taskId.isBlank()) return null;
        List<RoutingDecisionRecord> decisions = taskQuery.findRoutingDecisionsByTask(taskId, 1);
        if (decisions == null || decisions.isEmpty()) return null;
        return decisions.stream().findFirst().orElse(null);
    }

    private String failureReasonCategory(TaskRecord task, DispatchUserFacingError userFacingError) {
        TaskStatus status = task.getStatus();
        if (status == TaskStatus.RETRY_WAIT || task.getNextDispatchAttemptAt() != null || hasText(task.getDispatchRetryReason())) {
            return "WAITING_RETRY";
        }
        if (status == TaskStatus.DEAD_LETTER) return "DEAD_LETTER";
        if (status == TaskStatus.ESCALATED) return "ESCALATED";
        if (status == TaskStatus.ORPHANED || status == TaskStatus.RECONCILING) return "NEEDS_OPERATOR_RECONCILIATION";
        if (status != null && status.isTerminal()) return "TERMINAL_FAILURE";
        if (userFacingError != null && userFacingError.getCode() != null) return "DISPATCH_BLOCKED";
        return "UNKNOWN";
    }

    private String blockedReason(TaskRecord task, DispatchUserFacingError userFacingError) {
        TaskStatus status = task.getStatus();
        if (status == TaskStatus.RETRY_WAIT || task.getNextDispatchAttemptAt() != null || hasText(task.getDispatchRetryReason())) {
            return null;
        }
        if (status != null && status.isTerminal()) return null;
        if (userFacingError != null && userFacingError.getCode() != null) return userFacingError.getCode().name();
        return null;
    }

    private String failureReason(TaskRecord task) {
        TaskStatus status = task.getStatus();
        if (status != null && status.isTerminal()) return firstNonBlank(task.getLifecycleReason(), task.getErrorCode());
        if (status == TaskStatus.FAILED || status == TaskStatus.DEAD_LETTER || status == TaskStatus.ESCALATED) {
            return firstNonBlank(task.getLifecycleReason(), task.getErrorCode());
        }
        return null;
    }

    private String dispatchWaitReason(TaskRecord task, DispatchUserFacingError userFacingError) {
        if (task.getStatus() == TaskStatus.RETRY_WAIT || task.getNextDispatchAttemptAt() != null || hasText(task.getDispatchRetryReason())) {
            if (hasText(task.getDispatchRetryReason())) return task.getDispatchRetryReason();
            if (userFacingError != null) return userFacingError.toLegacyDecisionReason();
            return firstNonBlank(task.getLifecycleReason(), "Task is waiting for the next dispatch retry.");
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> actionHints(TaskRecord task) {
        Map<String, Object> actions = new LinkedHashMap<>();
        TaskStatus status = task.getStatus();
        actions.put("manualRetry", status == TaskStatus.DEAD_LETTER || status == TaskStatus.ESCALATED || status == TaskStatus.FAILED || status == TaskStatus.ORPHANED || status == TaskStatus.RECONCILING);
        actions.put("escalate", status == TaskStatus.FAILED || status == TaskStatus.RETRY_WAIT || status == TaskStatus.ORPHANED || status == TaskStatus.RECONCILING);
        actions.put("deadLetter", status == TaskStatus.FAILED || status == TaskStatus.ESCALATED || status == TaskStatus.ORPHANED || status == TaskStatus.RECONCILING);
        actions.put("viewTimeline", true);
        return actions;
    }

    private TaskRecord newerTask(TaskRecord a, TaskRecord b) {
        return taskSortTime(a).isAfter(taskSortTime(b)) ? a : b;
    }

    private OffsetDateTime taskSortTime(TaskRecord task) {
        return first(task.getUpdatedAt(), task.getCreatedAt(), OffsetDateTime.MIN);
    }

    private OffsetDateTime eventSortTime(DispatchTimelineEvent event) {
        return first(event.occurredAt(), OffsetDateTime.MIN);
    }

    private DispatchTimelineEvent event(int sequence, OffsetDateTime occurredAt, String stage, String action, String status, String severity,
                                        String source, String message, Map<String, String> references, Map<String, Object> details) {
        return new DispatchTimelineEvent(sequence, occurredAt, stage, action, status, severity, source, message,
                references == null ? Map.of() : references, details == null ? Map.of() : details);
    }

    private Map<String, String> refs(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        if (keyValues == null) return map;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String value = keyValues[i + 1];
            if (value != null && !value.isBlank()) {
                map.put(keyValues[i], value);
            }
        }
        return map;
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (keyValues == null) return map;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key != null && value != null) {
                map.put(String.valueOf(key), value);
            }
        }
        return map;
    }

    private String terminalAction(TaskStatus status) {
        if (status == TaskStatus.SUCCEEDED || status == TaskStatus.COMPLETED) return "TASK_SUCCEEDED";
        if (status == TaskStatus.DEAD_LETTER) return "TASK_DEAD_LETTERED";
        if (status == TaskStatus.ESCALATED) return "TASK_ESCALATED";
        if (status == TaskStatus.FAILED) return "TASK_FAILED";
        if (status == TaskStatus.CANCELLED) return "TASK_CANCELLED";
        return "TASK_TERMINAL";
    }

    private String terminalSeverity(TaskStatus status) {
        if (status == TaskStatus.SUCCEEDED || status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED) return "INFO";
        if (status == TaskStatus.ESCALATED) return "WARN";
        return "ERROR";
    }

    private String executionCompletionAction(TaskExecutionAttempt attempt) {
        if (attempt.getStatus() == null) return "EXECUTION_COMPLETED";
        return switch (attempt.getStatus()) {
            case SUCCEEDED -> "EXECUTION_SUCCEEDED";
            case FAILED -> "EXECUTION_FAILED";
            case STALE_CALLBACK_REJECTED -> "CALLBACK_REJECTED_STALE_FENCE";
            default -> "EXECUTION_COMPLETED";
        };
    }

    private String executionCompletionSeverity(TaskExecutionAttempt attempt) {
        if (attempt.getStatus() == null) return "INFO";
        return switch (attempt.getStatus()) {
            case SUCCEEDED -> "INFO";
            case FAILED, STALE_CALLBACK_REJECTED -> "WARN";
            default -> "INFO";
        };
    }

    private String auditSeverity(DispatchAttemptHistoryRecord history) {
        String status = history.getStatus();
        String eventType = history.getEventType();
        if (contains(status, "FAILED") || contains(status, "DEAD") || contains(eventType, "FAILED") || contains(eventType, "DEAD")) return "ERROR";
        if (contains(status, "RETRY") || contains(status, "UNCONFIRMED") || contains(eventType, "RETRY") || contains(eventType, "STALE")) return "WARN";
        return "INFO";
    }

    private boolean contains(String value, String token) {
        return value != null && token != null && value.toUpperCase().contains(token.toUpperCase());
    }

    private String status(Object status) {
        return status == null ? null : String.valueOf(status);
    }

    @SafeVarargs
    private final <T> T first(T... values) {
        if (values == null) return null;
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }
}
