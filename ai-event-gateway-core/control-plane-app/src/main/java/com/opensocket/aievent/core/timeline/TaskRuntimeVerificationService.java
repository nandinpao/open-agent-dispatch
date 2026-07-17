package com.opensocket.aievent.core.timeline;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceStage;
import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceView;
import com.opensocket.aievent.core.task.evidence.TaskDispatchRecoveryAction;
import com.opensocket.aievent.core.task.evidence.TaskRuntimeVerificationStep;
import com.opensocket.aievent.core.task.evidence.TaskRuntimeVerificationView;

@Service
public class TaskRuntimeVerificationService {
    private static final int DEFAULT_TIMEOUT_SECONDS = 90;
    private static final int DEFAULT_LIMIT = 200;

    private final TaskDispatchEvidenceService evidenceService;

    public TaskRuntimeVerificationService(TaskDispatchEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    public TaskRuntimeVerificationView verify(String taskId, int timeoutSeconds, int limit) {
        int safeTimeout = Math.max(5, Math.min(timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds, 3600));
        int safeLimit = Math.max(20, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, 500));
        TaskDispatchEvidenceView evidence = evidenceService.evidence(taskId, safeLimit);
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = evidence.getTask();
        DispatchRequest latest = latestDispatchRequest(evidence.getDispatchRequests());
        RoutingDecisionRecord routing = evidence.getLatestRoutingDecision();
        OffsetDateTime startedAt = first(
                task == null ? null : task.getCreatedAt(),
                latest == null ? null : latest.getCreatedAt(),
                generatedAt);
        long elapsedSeconds = Math.max(0L, Duration.between(startedAt, generatedAt).getSeconds());

        List<TaskRuntimeVerificationStep> steps = new ArrayList<>();
        steps.add(stepFromEvidence("CONTRACT_READY", "Contract Ready", evidenceStage(evidence, "TASK_CONTRACT"),
                "Dispatch contract must be ACTIVE before runtime delivery can be trusted."));
        steps.add(testTaskCreatedStep(task));
        steps.add(routingSelectedStep(routing));
        steps.add(dispatchRequestCreatedStep(latest));
        steps.add(runtimeDeliveredStep(latest));
        steps.add(agentAckStep(evidence, latest));
        steps.add(agentResultStep(evidence, latest));
        steps.add(callbackInboxStep(evidence));
        steps.add(taskTerminalStep(task, latest));

        TaskRuntimeVerificationStep firstBlocking = steps.stream()
                .filter(step -> isBlocking(step.getStatus()))
                .findFirst()
                .orElse(null);
        TaskRuntimeVerificationStep firstPending = steps.stream()
                .filter(step -> "PENDING".equalsIgnoreCase(step.getStatus()))
                .findFirst()
                .orElse(null);
        boolean completed = firstBlocking == null && firstPending == null;
        boolean timedOut = !completed && firstBlocking == null && elapsedSeconds >= safeTimeout;
        TaskRuntimeVerificationStep current = firstBlocking == null ? firstPending : firstBlocking;
        if (timedOut && current != null && blank(current.getBlockingCode())) {
            current.setStatus("TIMED_OUT");
            current.setBlockingCode("RUNTIME_VERIFICATION_TIMEOUT");
            current.setNextAction(nextActionForStep(current.getStep()));
            current.setSummary(current.getSummary() + " Verification timeout reached before this step completed.");
            firstBlocking = current;
        }

        TaskRuntimeVerificationView view = new TaskRuntimeVerificationView();
        view.setTaskId(taskId);
        view.setEvidence(evidence);
        view.setSteps(steps);
        view.setSuggestedActions(actions(taskId, evidence, latest, routing));
        view.setTimeoutSeconds(safeTimeout);
        view.setElapsedSeconds(elapsedSeconds);
        view.setTimedOut(timedOut);
        view.setStartedAt(startedAt);
        view.setGeneratedAt(generatedAt);
        view.setSelectedAgentId(firstNonBlank(routing == null ? null : routing.getSelectedAgentId(), latest == null ? null : latest.getAgentId()));
        view.setDispatchRequestId(latest == null ? null : latest.getDispatchRequestId());
        view.setCurrentStep(current == null ? "TASK_COMPLETED" : current.getStep());
        if (completed) {
            view.setStatus("COMPLETED");
            view.setSummary("Runtime E2E verification completed: routing, delivery, ACK/RESULT, callback inbox and terminal task evidence are present.");
        } else if (firstBlocking != null || timedOut) {
            TaskRuntimeVerificationStep blocked = firstBlocking == null ? current : firstBlocking;
            view.setStatus(timedOut ? "TIMED_OUT" : "BLOCKED");
            view.setSummary("Runtime E2E verification is blocked at " + (blocked == null ? "UNKNOWN" : blocked.getStep()) + ".");
            if (blocked != null) {
                view.setFirstBlockingStep(blocked.getStep());
                view.setFirstBlockingCode(firstNonBlank(blocked.getBlockingCode(), "RUNTIME_VERIFICATION_BLOCKED"));
                view.setFirstBlockingReason(blocked.getSummary());
            }
        } else {
            view.setStatus("IN_PROGRESS");
            view.setSummary("Runtime E2E verification is waiting at " + (current == null ? "UNKNOWN" : current.getStep()) + ".");
        }
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("authority", "P6_RUNTIME_DELIVERY_E2E_VERIFICATION");
        diagnostics.put("timeoutSeconds", safeTimeout);
        diagnostics.put("elapsedSeconds", elapsedSeconds);
        diagnostics.put("latestDispatchStatus", latest == null || latest.getStatus() == null ? null : latest.getStatus().name());
        diagnostics.put("evidenceStatus", evidence.getStatus());
        view.setDiagnostics(diagnostics);
        return view;
    }

    private TaskRuntimeVerificationStep stepFromEvidence(String stepCode, String title, TaskDispatchEvidenceStage source, String fallback) {
        if (source == null) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of(stepCode, "PENDING", title, fallback);
            step.setNextAction(nextActionForStep(stepCode));
            return step;
        }
        String status = mapEvidenceStatus(source.getStatus());
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of(stepCode, status, title, firstNonBlank(source.getSummary(), fallback));
        step.setBlockingCode(source.getBlockingCode());
        step.setNextAction(source.getNextAction());
        step.setDetails(source.getDetails());
        return step;
    }

    private TaskRuntimeVerificationStep testTaskCreatedStep(TaskRecord task) {
        if (task == null) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("TEST_TASK_CREATED", "BLOCKED", "Test Task Created", "Task record was not found.");
            step.setBlockingCode("TASK_NOT_FOUND");
            return step;
        }
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("TEST_TASK_CREATED", "PASS", "Test Task Created", "Task record exists in Core.");
        step.setObservedAt(task.getCreatedAt());
        step.setDetails(details("taskStatus", task.getStatus(), "sourceSystem", task.getSourceSystem(), "taskType", task.getTaskType()));
        return step;
    }

    private TaskRuntimeVerificationStep routingSelectedStep(RoutingDecisionRecord routing) {
        if (routing == null) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("ROUTING_SELECTED_AGENT", "PENDING", "Routing Selected Agent", "No routing decision has been recorded yet.");
            step.setNextAction("Run Task-level Readiness");
            return step;
        }
        boolean selected = !blank(routing.getSelectedAgentId());
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("ROUTING_SELECTED_AGENT", selected ? "PASS" : "BLOCKED", "Routing Selected Agent",
                selected ? "Routing selected Agent " + routing.getSelectedAgentId() + "." : firstNonBlank(routing.getDecisionReason(), "Routing did not select an Agent."));
        step.setBlockingCode(selected ? null : "ROUTING_NO_SELECTED_AGENT");
        step.setNextAction(selected ? null : "Run Task-level Readiness");
        step.setObservedAt(routing.getCreatedAt());
        step.setDetails(details("decisionId", routing.getDecisionId(), "selectedScore", routing.getSelectedScore(), "routingPolicy", routing.getRoutingPolicy()));
        return step;
    }

    private TaskRuntimeVerificationStep dispatchRequestCreatedStep(DispatchRequest latest) {
        if (latest == null) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("DISPATCH_REQUEST_CREATED", "PENDING", "Dispatch Request Created", "No dispatch request has been created for this task yet.");
            step.setNextAction("Retry Dispatch");
            return step;
        }
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("DISPATCH_REQUEST_CREATED", "PASS", "Dispatch Request Created", "Dispatch request exists for this task.");
        step.setObservedAt(latest.getCreatedAt());
        step.setDetails(dispatchDetails(latest));
        return step;
    }

    private TaskRuntimeVerificationStep runtimeDeliveredStep(DispatchRequest latest) {
        if (latest == null) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("RUNTIME_DELIVERED", "PENDING", "Runtime Delivered", "Waiting for a dispatch request before runtime delivery can be verified.");
            step.setNextAction("Retry Dispatch");
            return step;
        }
        if (isDispatchFailed(latest)) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("RUNTIME_DELIVERED", "BLOCKED", "Runtime Delivered", firstNonBlank(latest.getLastError(), "Dispatch request failed before Agent ACK."));
            step.setBlockingCode("RUNTIME_DELIVERY_FAILED");
            step.setNextAction("Retry Latest Dispatch Request");
            step.setObservedAt(first(latest.getFailedAt(), latest.getTimedOutAt(), latest.getDeadLetterAt(), latest.getUpdatedAt()));
            step.setDetails(dispatchDetails(latest));
            return step;
        }
        if (latest.getDispatchedAt() != null || dispatchStatusAtLeast(latest, DispatchRequestStatus.DISPATCHED)) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("RUNTIME_DELIVERED", "PASS", "Runtime Delivered", "Dispatch request reached runtime delivery state.");
            step.setObservedAt(first(latest.getDispatchedAt(), latest.getUpdatedAt()));
            step.setDetails(dispatchDetails(latest));
            return step;
        }
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("RUNTIME_DELIVERED", "PENDING", "Runtime Delivered", "Dispatch request exists but has not reached runtime delivery yet.");
        step.setNextAction("Open Agent Diagnostics");
        step.setDetails(dispatchDetails(latest));
        return step;
    }

    private TaskRuntimeVerificationStep agentAckStep(TaskDispatchEvidenceView evidence, DispatchRequest latest) {
        TaskDispatchEvidenceStage stage = evidenceStage(evidence, "AGENT_ACK");
        if (latest != null && dispatchStatusAtLeast(latest, DispatchRequestStatus.ACKED)) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("AGENT_ACK", "PASS", "Agent ACK", "Agent ACK was recorded by Core.");
            step.setObservedAt(first(latest.getUpdatedAt(), latest.getDispatchedAt()));
            step.setDetails(dispatchDetails(latest));
            return step;
        }
        TaskRuntimeVerificationStep step = stepFromEvidence("AGENT_ACK", "Agent ACK", stage, "Waiting for Agent ACK callback.");
        if ("PENDING".equalsIgnoreCase(step.getStatus())) step.setNextAction("Open Agent Diagnostics");
        return step;
    }

    private TaskRuntimeVerificationStep agentResultStep(TaskDispatchEvidenceView evidence, DispatchRequest latest) {
        TaskDispatchEvidenceStage stage = evidenceStage(evidence, "AGENT_RESULT");
        if (latest != null && dispatchStatusAtLeast(latest, DispatchRequestStatus.COMPLETED)) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("AGENT_RESULT", "PASS", "Agent RESULT / ERROR", "Agent terminal callback was recorded by Core.");
            step.setObservedAt(first(latest.getCompletedAt(), latest.getFailedAt(), latest.getUpdatedAt()));
            step.setDetails(dispatchDetails(latest));
            return step;
        }
        TaskRuntimeVerificationStep step = stepFromEvidence("AGENT_RESULT", "Agent RESULT / ERROR", stage, "Waiting for Agent RESULT or ERROR callback.");
        if ("PENDING".equalsIgnoreCase(step.getStatus())) step.setNextAction("Open Agent Diagnostics");
        return step;
    }

    private TaskRuntimeVerificationStep callbackInboxStep(TaskDispatchEvidenceView evidence) {
        TaskRuntimeVerificationStep step = stepFromEvidence("CALLBACK_INBOX", "Callback Inbox", evidenceStage(evidence, "CALLBACK_INBOX"), "Waiting for callback inbox evidence.");
        if ("PENDING".equalsIgnoreCase(step.getStatus())) step.setNextAction("Open Callback Inbox");
        return step;
    }

    private TaskRuntimeVerificationStep taskTerminalStep(TaskRecord task, DispatchRequest latest) {
        boolean terminal = task != null && task.getStatus() != null && task.getStatus().isTerminal();
        if (terminal || (latest != null && dispatchStatusAtLeast(latest, DispatchRequestStatus.COMPLETED))) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("TASK_COMPLETED", "PASS", "Task Completed", "Task reached a terminal state after runtime execution.");
            step.setObservedAt(first(task == null ? null : task.getTerminalAt(), latest == null ? null : latest.getCompletedAt(), task == null ? null : task.getUpdatedAt()));
            step.setDetails(details("taskStatus", task == null ? null : task.getStatus(), "dispatchStatus", latest == null ? null : latest.getStatus()));
            return step;
        }
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("TASK_COMPLETED", "PENDING", "Task Completed", "Task has not reached terminal state yet.");
        step.setNextAction("Wait or open Task Diagnostics");
        step.setDetails(details("taskStatus", task == null ? null : task.getStatus(), "dispatchStatus", latest == null ? null : latest.getStatus()));
        return step;
    }

    private List<TaskDispatchRecoveryAction> actions(String taskId, TaskDispatchEvidenceView evidence, DispatchRequest latest, RoutingDecisionRecord routing) {
        List<TaskDispatchRecoveryAction> actions = new ArrayList<>(evidence.getSuggestedActions() == null ? List.of() : evidence.getSuggestedActions());
        actions.add(TaskDispatchRecoveryAction.of("OPEN_TASK_EVIDENCE", "Open Evidence Timeline", "Open the task detail evidence timeline.", "/tasks/" + url(taskId), "GET", "LOW", true));
        String agentId = firstNonBlank(latest == null ? null : latest.getAgentId(), routing == null ? null : routing.getSelectedAgentId());
        if (!blank(agentId)) {
            actions.add(TaskDispatchRecoveryAction.of("OPEN_AGENT_DIAGNOSTICS", "Open Agent Diagnostics", "Open the selected Agent to inspect runtime connection, capacity and recent tasks.", "/agents/" + url(agentId) + "?tab=diagnostics", "GET", "LOW", true));
        }
        if (latest != null && !blank(latest.getDispatchRequestId())) {
            actions.add(TaskDispatchRecoveryAction.of("RETRY_LATEST_DISPATCH", "Retry Latest Dispatch", "Retry the latest dispatch request after fixing runtime delivery or callback issues.", "/admin/dispatch-requests/" + url(latest.getDispatchRequestId()) + "/retry", "POST", "MIDDLE", true));
            actions.add(TaskDispatchRecoveryAction.of("DEAD_LETTER_LATEST_DISPATCH", "Move Latest Dispatch to Dead Letter", "Move the latest dispatch request to dead letter when it is unrecoverable.", "/admin/recovery/actions/dispatch-requests/" + url(latest.getDispatchRequestId()) + "/dead-letter", "POST", "HIGH", true));
        }
        return actions;
    }

    private DispatchRequest latestDispatchRequest(List<DispatchRequest> requests) {
        if (requests == null || requests.isEmpty()) return null;
        return requests.stream().max(Comparator.comparing(request -> first(request.getUpdatedAt(), request.getCreatedAt(), OffsetDateTime.MIN))).orElse(null);
    }

    private TaskDispatchEvidenceStage evidenceStage(TaskDispatchEvidenceView evidence, String stage) {
        if (evidence == null || evidence.getStages() == null || stage == null) return null;
        return evidence.getStages().stream().filter(item -> stage.equalsIgnoreCase(item.getStage())).findFirst().orElse(null);
    }

    private String mapEvidenceStatus(String status) {
        if (status == null) return "PENDING";
        String normalized = status.toUpperCase();
        if (normalized.contains("PASS") || normalized.contains("READY") || normalized.contains("COMPLETE")) return "PASS";
        if (normalized.contains("BLOCK") || normalized.contains("ERROR") || normalized.contains("FAIL")) return "BLOCKED";
        return "PENDING";
    }

    private boolean isBlocking(String status) {
        return contains(status, "BLOCK") || contains(status, "ERROR") || contains(status, "FAIL") || contains(status, "DEAD") || contains(status, "TIME");
    }

    private boolean isDispatchFailed(DispatchRequest request) {
        if (request == null) return false;
        String status = request.getStatus() == null ? null : request.getStatus().name();
        return request.getFailedAt() != null || request.getTimedOutAt() != null || request.getDeadLetterAt() != null
                || contains(status, "FAILED") || contains(status, "TIMED_OUT") || contains(status, "DEAD_LETTER");
    }

    private boolean dispatchStatusAtLeast(DispatchRequest request, DispatchRequestStatus expected) {
        if (request == null || request.getStatus() == null || expected == null) return false;
        return request.getStatus().ordinal() >= expected.ordinal();
    }

    private Map<String, Object> dispatchDetails(DispatchRequest request) {
        return details(
                "dispatchRequestId", request == null ? null : request.getDispatchRequestId(),
                "status", request == null ? null : request.getStatus(),
                "agentId", request == null ? null : request.getAgentId(),
                "gatewayNode", request == null ? null : request.getOwnerGatewayNodeId(),
                "agentSessionId", request == null ? null : request.getAgentSessionId(),
                "attemptCount", request == null ? null : request.getAttemptCount(),
                "lastError", request == null ? null : request.getLastError());
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (keyValues == null) return values;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] != null && keyValues[i + 1] != null) {
                values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return values;
    }

    private String nextActionForStep(String step) {
        if (contains(step, "CONTRACT")) return "Repair Dispatch Contract";
        if (contains(step, "ROUTING")) return "Run Task-level Readiness";
        if (contains(step, "DELIVER") || contains(step, "ACK") || contains(step, "RESULT")) return "Open Agent Diagnostics";
        if (contains(step, "CALLBACK")) return "Open Callback Inbox";
        return "Open Task Diagnostics";
    }

    private boolean contains(String value, String token) { return value != null && token != null && value.toUpperCase().contains(token.toUpperCase()); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
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
