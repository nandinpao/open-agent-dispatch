package com.opensocket.aievent.core.timeline;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityService;
import com.opensocket.aievent.core.agent.eligibility.TaskEligibleAgentsResponse;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceStage;
import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceView;
import com.opensocket.aievent.core.task.evidence.TaskDispatchRecoveryAction;

@Service
public class TaskDispatchEvidenceService {
    private final TaskOperationalQuery taskQuery;
    private final ExecutionOperationalQuery executionQuery;
    private final DispatchTimelineService timelineService;
    private final DispatchEligibilityService dispatchEligibilityService;

    @Autowired(required = false)
    private TaskIssueLinkRepository taskIssueLinkRepository = TaskIssueLinkRepository.noop();

    public TaskDispatchEvidenceService(TaskOperationalQuery taskQuery,
                                       ExecutionOperationalQuery executionQuery,
                                       DispatchTimelineService timelineService,
                                       DispatchEligibilityService dispatchEligibilityService) {
        this.taskQuery = taskQuery;
        this.executionQuery = executionQuery;
        this.timelineService = timelineService;
        this.dispatchEligibilityService = dispatchEligibilityService;
    }

    public TaskDispatchEvidenceView evidence(String taskId, int limit) {
        TaskRecord task = requireTask(taskId);
        int safeLimit = safeLimit(limit);
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        DispatchTimelineResponse timeline = timelineService.timeline(taskId, safeLimit);
        List<DispatchRequest> dispatchRequests = executionQuery.findDispatchRequestsByTask(taskId, safeLimit);
        RoutingDecisionRecord latestRouting = latestRoutingDecision(taskId);
        TaskIssueLink issue = taskIssueLinkRepository.findByTaskId(taskId).orElse(null);
        TaskEligibleAgentsResponse eligibleAgents = eligibleAgentsForTask(task);

        List<TaskDispatchEvidenceStage> stages = new ArrayList<>();
        stages.add(eventStage(task));
        stages.add(taskStage(task));
        stages.add(flowStage(task));
        stages.add(ruleStage(task));
        stages.add(candidateStage(task, eligibleAgents));
        stages.add(capabilityStage(task, eligibleAgents));
        stages.add(agentRuntimeStage(task, eligibleAgents));
        stages.add(assignmentStage(task, latestRouting));
        stages.add(dispatchRequestStage(dispatchRequests));
        stages.add(runtimeDeliveryStage(dispatchRequests));
        stages.add(callbackStage(timeline, "ACK", "Agent ACK", "AGENT_ACK", "RESULT_TIMEOUT"));
        stages.add(callbackStage(timeline, "RESULT", "Agent Result", "AGENT_RESULT", "RESULT_TIMEOUT"));

        TaskDispatchEvidenceStage firstBlocking = stages.stream()
                .filter(stage -> "BLOCKED".equalsIgnoreCase(stage.getStatus()) || "ERROR".equalsIgnoreCase(stage.getStatus()))
                .findFirst()
                .orElse(null);

        TaskDispatchEvidenceView view = new TaskDispatchEvidenceView();
        view.setTaskId(taskId);
        view.setTask(task);
        view.setContractReadiness(null);
        view.setEligibleAgents(eligibleAgents);
        view.setLatestRoutingDecision(latestRouting);
        view.setDispatchRequests(dispatchRequests);
        view.setTimeline(timeline);
        view.setIssueTracking(issue);
        view.setStages(stages);
        view.setSuggestedActions(actions(task, dispatchRequests));
        view.setGeneratedAt(generatedAt);
        if (firstBlocking == null) {
            view.setStatus("READY_OR_IN_PROGRESS");
            view.setSummary("Runtime Decision Chain has no blocking stage yet.");
        } else {
            view.setStatus("BLOCKED");
            view.setSummary("Runtime Decision Chain is blocked at " + firstBlocking.getStage() + ".");
            view.setFirstBlockingStage(firstBlocking.getStage());
            view.setFirstBlockingCode(firstBlocking.getBlockingCode());
            view.setFirstBlockingReason(firstBlocking.getSummary());
        }
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("sourceSystem", task.getSourceSystem());
        diagnostics.put("taskType", resolvedTaskType(task));
        diagnostics.put("dispatchRequestCount", dispatchRequests.size());
        diagnostics.put("timelineEventCount", timeline == null || timeline.events() == null ? 0 : timeline.events().size());
        diagnostics.put("authority", "FLOW_RULE_TO_DISPATCH_REQUEST_TO_NETTY_ACK_RESULT");
        view.setDiagnostics(diagnostics);
        return view;
    }

    private TaskEligibleAgentsResponse eligibleAgentsForTask(TaskRecord task) {
        try {
            return dispatchEligibilityService.eligibleAgents(task, 500);
        } catch (RuntimeException ex) {
            TaskEligibleAgentsResponse response = new TaskEligibleAgentsResponse();
            response.setTaskId(task.getTaskId());
            response.setEligibleAgents(List.of());
            response.setBlockedAgents(List.of());
            response.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return response;
        }
    }


    private TaskDispatchEvidenceStage eventStage(TaskRecord task) {
        return TaskDispatchEvidenceStage.of("EVENT_ACCEPTED", "PASS", "Event Accepted",
                blank(task.getSourceEventId()) ? "Core has a task record for this event." : "Event " + task.getSourceEventId() + " was accepted.")
                .withDetail("sourceSystem", task.getSourceSystem())
                .withDetail("sourceEventId", task.getSourceEventId());
    }

    private TaskDispatchEvidenceStage taskStage(TaskRecord task) {
        return TaskDispatchEvidenceStage.of("TASK_CREATED", "PASS", "Task Created", "Task " + task.getTaskId() + " exists in Core.")
                .withDetail("taskId", task.getTaskId())
                .withDetail("tenantId", task.getTenantId());
    }

    private TaskDispatchEvidenceStage flowStage(TaskRecord task) {
        if (!blank(task.getMatchedFlowId())) {
            return TaskDispatchEvidenceStage.of("FLOW_MATCHED", "PASS", "Dispatch Flow Matched", "Matched Flow " + task.getMatchedFlowId() + ".")
                    .withDetail("matchedFlowId", task.getMatchedFlowId());
        }
        return TaskDispatchEvidenceStage.blocked("FLOW_MATCHED", "Dispatch Flow Matched", "No enabled Dispatch Flow matched this event.", "NO_MATCHING_FLOW", "Open Dispatch Flow")
                .withDetail("sourceSystem", task.getSourceSystem())
                .withDetail("objectType", task.getObjectType())
                .withDetail("eventType", task.getEventType())
                .withDetail("errorCode", task.getErrorCode());
    }

    private TaskDispatchEvidenceStage ruleStage(TaskRecord task) {
        if (blank(task.getMatchedFlowId())) {
            return TaskDispatchEvidenceStage.of("RULE_MATCHED", "SKIPPED", "Flow Rule Matched", "Rule matching waits until a Dispatch Flow matches.");
        }
        if (!blank(task.getMatchedRuleId())) {
            return TaskDispatchEvidenceStage.of("RULE_MATCHED", "PASS", "Flow Rule Matched", "Matched Rule " + task.getMatchedRuleId() + ".")
                    .withDetail("matchedRuleId", task.getMatchedRuleId());
        }
        return TaskDispatchEvidenceStage.blocked("RULE_MATCHED", "Flow Rule Matched", "A Dispatch Flow matched, but no enabled Flow Rule matched this event condition.", "NO_MATCHING_RULE", "Review Flow Rule")
                .withDetail("matchedFlowId", task.getMatchedFlowId());
    }

    private TaskDispatchEvidenceStage candidateStage(TaskRecord task, TaskEligibleAgentsResponse eligibleAgents) {
        if (blank(task.getMatchedFlowId()) || blank(task.getMatchedRuleId())) {
            return TaskDispatchEvidenceStage.of("CANDIDATES_FOUND", "SKIPPED", "Flow Agent Candidates", "Candidate lookup waits until Flow and Rule match.");
        }
        int eligible = eligibleAgents == null || eligibleAgents.getEligibleAgents() == null ? 0 : eligibleAgents.getEligibleAgents().size();
        int blocked = eligibleAgents == null || eligibleAgents.getBlockedAgents() == null ? 0 : eligibleAgents.getBlockedAgents().size();
        String assignedAgentId = taskAssignedAgent(task);
        if (eligible > 0 || !blank(assignedAgentId)) {
            return TaskDispatchEvidenceStage.of("CANDIDATES_FOUND", "PASS", "Flow Agent Candidates", Math.max(eligible, 1) + " Flow Agent candidate(s) available.")
                    .withDetail("eligibleCount", eligible).withDetail("blockedCount", blocked).withDetail("assignedAgentId", assignedAgentId);
        }
        return TaskDispatchEvidenceStage.blocked("CANDIDATES_FOUND", "Flow Agent Candidates", "Flow Rule matched, but no Flow Agent candidate is available.", "NO_FLOW_AGENT", "Select Flow Agent")
                .withDetail("eligibleCount", eligible).withDetail("blockedCount", blocked);
    }

    private TaskDispatchEvidenceStage capabilityStage(TaskRecord task, TaskEligibleAgentsResponse eligibleAgents) {
        List<String> required = task.getRequiredCapabilities() == null ? List.of() : task.getRequiredCapabilities();
        if (required.isEmpty()) {
            return TaskDispatchEvidenceStage.of("CAPABILITY_CHECK", "PASS", "Required Capability", "No Required Capability is configured for this Flow Rule.");
        }
        int eligible = eligibleAgents == null || eligibleAgents.getEligibleAgents() == null ? 0 : eligibleAgents.getEligibleAgents().size();
        if (eligible > 0 || !blank(taskAssignedAgent(task))) {
            return TaskDispatchEvidenceStage.of("CAPABILITY_CHECK", "PASS", "Required Capability", "Required Capability matched at least one Flow Agent.")
                    .withDetail("requiredCapabilities", required);
        }
        return TaskDispatchEvidenceStage.blocked("CAPABILITY_CHECK", "Required Capability", "No Flow Agent currently has every Required Capability for this task.", "MISSING_REQUIRED_CAPABILITY", "Review Required Capability")
                .withDetail("requiredCapabilities", required);
    }

    private TaskDispatchEvidenceStage agentRuntimeStage(TaskRecord task, TaskEligibleAgentsResponse eligibleAgents) {
        String reason = firstBlockedCandidateReason(eligibleAgents);
        String assignedAgentId = taskAssignedAgent(task);
        if (!blank(assignedAgentId)) {
            return TaskDispatchEvidenceStage.of("AGENT_RUNTIME_CHECK", "PASS", "Agent Runtime / Capacity", "Selected Agent " + assignedAgentId + " is available for dispatch.")
                    .withDetail("agentId", assignedAgentId);
        }
        String standard = standardBlockerCode(reason);
        if ("AGENT_OFFLINE".equals(standard)) {
            return TaskDispatchEvidenceStage.blocked("AGENT_RUNTIME_CHECK", "Agent Runtime / Capacity", "Flow Agent is offline or has no active runtime session.", "AGENT_OFFLINE", "Open Agent");
        }
        if ("AGENT_CAPACITY_FULL".equals(standard)) {
            return TaskDispatchEvidenceStage.blocked("AGENT_RUNTIME_CHECK", "Agent Runtime / Capacity", "Flow Agent is online but has no available capacity.", "AGENT_CAPACITY_FULL", "Open Agent");
        }
        return TaskDispatchEvidenceStage.of("AGENT_RUNTIME_CHECK", "PENDING", "Agent Runtime / Capacity", "No selected Agent yet, or runtime evidence has not been recorded.");
    }

    private TaskDispatchEvidenceStage routingStage(RoutingDecisionRecord decision) {
        if (decision == null) {
            return TaskDispatchEvidenceStage.of("ROUTING_SCORE", "PENDING", "Routing Score", "No routing decision has been recorded yet.");
        }
        boolean selected = !blank(decision.getSelectedAgentId());
        String summary = selected
                ? "Routing selected Agent " + decision.getSelectedAgentId() + " with score " + decision.getSelectedScore() + "."
                : firstNonBlank(decision.getDecisionReason(), "Routing did not select an Agent.");
        String code = decision.getUserFacingError() == null || decision.getUserFacingError().getCode() == null ? null : decision.getUserFacingError().getCode().name();
        return new TaskDispatchEvidenceStage("ROUTING_SCORE", selected ? "PASS" : "BLOCKED", "Routing Score", summary,
                selected ? null : standardBlockerCode(firstNonBlank(code, decision.getDecisionReason())), selected ? null : "Review Flow Agent", details(
                "decisionId", decision.getDecisionId(),
                "routingPolicy", decision.getRoutingPolicy(),
                "routingStatus", decision.getStatus(),
                "selectedAgentId", decision.getSelectedAgentId(),
                "selectedScore", decision.getSelectedScore()
        ));
    }

    private TaskDispatchEvidenceStage assignmentStage(TaskRecord task, RoutingDecisionRecord decision) {
        String selectedAgentId = firstNonBlank(taskAssignedAgent(task), decision == null ? null : decision.getSelectedAgentId());
        if (!blank(selectedAgentId)) {
            return TaskDispatchEvidenceStage.of("ASSIGNMENT_CREATED", "PASS", "Assignment Created", "Task is assigned to Agent " + selectedAgentId + ".")
                    .withDetail("selectedAgentId", selectedAgentId);
        }
        return TaskDispatchEvidenceStage.of("ASSIGNMENT_CREATED", "PENDING", "Assignment Created", "Assignment will be created after Flow Agent, Capability, Runtime and Capacity checks pass.");
    }

    private TaskDispatchEvidenceStage dispatchRequestStage(List<DispatchRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return TaskDispatchEvidenceStage.of("DISPATCH_REQUEST_CREATED", "PENDING", "DispatchRequest Created", "DispatchRequest will be created after Assignment is available.");
        }
        DispatchRequest latest = requests.stream().max(Comparator.comparing(this::dispatchSortTime)).orElse(null);
        return TaskDispatchEvidenceStage.of("DISPATCH_REQUEST_CREATED", "PASS", "DispatchRequest Created", "DispatchRequest was persisted for Netty delivery.")
                .withDetail("dispatchRequestId", latest == null ? null : latest.getDispatchRequestId());
    }

    private TaskDispatchEvidenceStage runtimeDeliveryStage(List<DispatchRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return TaskDispatchEvidenceStage.of("RUNTIME_DELIVERY", "PENDING", "Runtime Delivery", "No dispatch request has been created yet.");
        }
        DispatchRequest latest = requests.stream().max(Comparator.comparing(this::dispatchSortTime)).orElse(null);
        String status = latest == null || latest.getStatus() == null ? "UNKNOWN" : latest.getStatus().name();
        boolean failed = contains(status, "FAILED") || contains(status, "DEAD") || latest.getFailedAt() != null || latest.getDeadLetterAt() != null;
        boolean dispatched = latest.getDispatchedAt() != null || contains(status, "DISPATCH") || contains(status, "DELIVER");
        return new TaskDispatchEvidenceStage("RUNTIME_DELIVERY", failed ? "ERROR" : dispatched ? "PASS" : "PENDING", "Runtime Delivery",
                failed ? firstNonBlank(latest.getLastError(), "Dispatch request failed before Agent execution.") : dispatched ? "Dispatch command was delivered or is being delivered." : "Dispatch request exists but has not reached runtime delivery yet.",
                failed ? "DISPATCH_DELIVERY_FAILED" : null, failed ? "Retry Dispatch" : null, details(
                "dispatchRequestId", latest == null ? null : latest.getDispatchRequestId(),
                "status", status,
                "attemptCount", latest == null ? 0 : latest.getAttemptCount()
        ));
    }

    private TaskDispatchEvidenceStage callbackStage(DispatchTimelineResponse timeline, String token, String title, String stage, String timeoutCode) {
        boolean seen = timeline != null && timeline.events() != null && timeline.events().stream()
                .anyMatch(event -> contains(event.action(), token) || contains(event.status(), token) || contains(event.message(), token));
        return TaskDispatchEvidenceStage.of(stage, seen ? "PASS" : "PENDING", title,
                seen ? title + " evidence was found in the Core timeline." : title + " has not been observed yet.")
                .withDetail("timeoutBlocker", timeoutCode);
    }

    private TaskDispatchEvidenceStage callbackInboxStage(DispatchTimelineResponse timeline) {
        long callbackEvents = timeline == null || timeline.events() == null ? 0 : timeline.events().stream()
                .filter(event -> "CALLBACK".equalsIgnoreCase(event.stage())).count();
        return TaskDispatchEvidenceStage.of("CALLBACK_INBOX", callbackEvents > 0 ? "PASS" : "PENDING", "Callback Inbox", callbackEvents > 0 ? callbackEvents + " callback timeline event(s) found." : "No callback inbox event has been observed yet.")
                .withDetail("callbackTimelineEvents", callbackEvents);
    }

    private TaskDispatchEvidenceStage issueStage(TaskIssueLink issue) {
        if (issue == null) {
            return TaskDispatchEvidenceStage.of("ISSUE_HISTORY", "PENDING", "Issue History", "No issue link is attached to this task yet.");
        }
        return TaskDispatchEvidenceStage.of("ISSUE_HISTORY", "PASS", "Issue History", "Issue link is available for this task.")
                .withDetail("issueId", issue.getIssueId()).withDetail("issueUrl", issue.getIssueUrl());
    }

    private List<TaskDispatchRecoveryAction> actions(TaskRecord task, List<DispatchRequest> dispatchRequests) {
        List<TaskDispatchRecoveryAction> actions = new ArrayList<>();
        actions.add(TaskDispatchRecoveryAction.of("OPEN_DISPATCH_FLOW", "Open Dispatch Flow", "Review the Dispatch Flow, Flow Rule, Flow Agent selection, and optional Required Capability for this task.", "/dispatch-flows?sourceSystem=" + url(normalize(task.getSourceSystem())) + "&taskType=" + url(resolvedTaskType(task)), "GET", "LOW", true));
        actions.add(TaskDispatchRecoveryAction.of("REVIEW_DISPATCH_FLOW", "Review Dispatch Flow", "Review the Dispatch Flow, Flow Rule, Flow Agent selection, and optional Required Capability for this task.", "/dispatch-flows?sourceSystem=" + url(normalize(task.getSourceSystem())) + "&taskType=" + url(resolvedTaskType(task)), "GET", "MIDDLE", true));
        actions.add(TaskDispatchRecoveryAction.of("OPEN_AGENT", "Open Agent", "Check Agent runtime, heartbeat, credential and capacity if this task reached Agent selection.", task.getMatchedFlowId() == null ? "/agents" : "/agents", "GET", "LOW", true));
        actions.add(TaskDispatchRecoveryAction.of("RETRY_DISPATCH", "Retry Dispatch", "Retry dispatch after the standard blocker is repaired.", "/admin/tasks/" + url(task.getTaskId()) + "/manual-retry", "POST", "MIDDLE", true));
        if (dispatchRequests != null && !dispatchRequests.isEmpty()) {
            DispatchRequest latest = dispatchRequests.stream().max(Comparator.comparing(this::dispatchSortTime)).orElse(null);
            if (latest != null && latest.getDispatchRequestId() != null) {
                actions.add(TaskDispatchRecoveryAction.of("RETRY_LATEST_DISPATCH_REQUEST", "Retry Latest DispatchRequest", "Retry the latest dispatch request after Agent runtime and Netty delivery are healthy.", "/admin/dispatch-requests/" + url(latest.getDispatchRequestId()) + "/retry", "POST", "MIDDLE", true));
            }
        }
        return actions;
    }

    private String firstBlockedCandidateReason(TaskEligibleAgentsResponse eligibleAgents) {
        if (eligibleAgents == null || eligibleAgents.getBlockedAgents() == null || eligibleAgents.getBlockedAgents().isEmpty()) return null;
        return eligibleAgents.getBlockedAgents().stream()
                .map(candidate -> firstNonBlank(candidate.getReason(), candidate.getDispatchStatus()))
                .filter(value -> !blank(value))
                .findFirst()
                .orElse(null);
    }

    private String standardBlockerCode(String raw) {
        String value = normalize(raw);
        if (blank(value)) return "NO_FLOW_AGENT";
        if (contains(value, "FLOW") && !contains(value, "AGENT")) return "NO_MATCHING_FLOW";
        if (contains(value, "RULE")) return "NO_MATCHING_RULE";
        if (contains(value, "CAPABILITY") || contains(value, "SKILL")) return "MISSING_REQUIRED_CAPABILITY";
        if (contains(value, "OFFLINE") || contains(value, "DISCONNECT") || contains(value, "RUNTIME_NOT_CONNECTED") || contains(value, "SESSION")) return "AGENT_OFFLINE";
        if (contains(value, "CAPACITY") || contains(value, "BUSY") || contains(value, "FULL")) return "AGENT_CAPACITY_FULL";
        if (contains(value, "DELIVERY") || contains(value, "GATEWAY") || contains(value, "DISPATCH_REQUEST")) return "DISPATCH_DELIVERY_FAILED";
        if (contains(value, "RESULT") || contains(value, "CALLBACK") || contains(value, "TIMEOUT")) return "RESULT_TIMEOUT";
        if (contains(value, "CANDIDATE") || contains(value, "ELIGIBLE") || contains(value, "AGENT")) return "NO_FLOW_AGENT";
        return "NO_FLOW_AGENT";
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (keyValues == null) return values;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key != null && value != null) {
                values.put(String.valueOf(key), value);
            }
        }
        return values;
    }

    private RoutingDecisionRecord latestRoutingDecision(String taskId) {
        List<RoutingDecisionRecord> decisions = taskQuery.findRoutingDecisionsByTask(taskId, 20);
        if (decisions == null || decisions.isEmpty()) return null;
        return decisions.stream().max(Comparator.comparing(record -> first(record.getCreatedAt(), OffsetDateTime.MIN))).orElse(null);
    }

    private TaskRecord requireTask(String taskId) {
        return taskQuery.findTask(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private OffsetDateTime dispatchSortTime(DispatchRequest request) {
        return first(request.getUpdatedAt(), request.getCreatedAt(), OffsetDateTime.MIN);
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500));
    }

    private String resolvedTaskType(TaskRecord task) {
        return task == null ? null : normalize(task.getEffectiveTaskTypeCode());
    }

    private String taskAssignedAgent(TaskRecord task) {
        RoutingDecisionRecord decision = latestRoutingDecision(task.getTaskId());
        return decision == null ? null : decision.getSelectedAgentId();
    }

    private String normalize(String value) { return value == null ? null : value.trim().toUpperCase().replace('-', '_').replace(' ', '_'); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean contains(String value, String token) { return value != null && token != null && value.toUpperCase().contains(token.toUpperCase()); }
    private boolean startsWith(String value, String token) { return value != null && token != null && value.toUpperCase().startsWith(token.toUpperCase()); }
    private String firstOf(List<String> values) { return values == null || values.isEmpty() ? null : values.get(0); }
    private String url(String value) { return value == null ? "" : value.replace(" ", "%20"); }

    @SafeVarargs
    private final <T> T first(T... values) {
        if (values == null) return null;
        for (T value : values) if (value != null) return value;
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }
}
