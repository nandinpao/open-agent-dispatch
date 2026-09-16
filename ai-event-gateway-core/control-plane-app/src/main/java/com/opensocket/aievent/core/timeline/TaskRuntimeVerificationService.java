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
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStage;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStageCode;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyStatus;
import com.opensocket.aievent.core.task.journey.TaskExecutionJourneyView;
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
    private final TaskExecutionJourneyService journeyService;

    public TaskRuntimeVerificationService(TaskDispatchEvidenceService evidenceService, TaskExecutionJourneyService journeyService) {
        this.evidenceService = evidenceService;
        this.journeyService = journeyService;
    }

    public TaskRuntimeVerificationView verify(String taskId, int timeoutSeconds, int limit) {
        int safeTimeout = Math.max(5, Math.min(timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds, 3600));
        int safeLimit = Math.max(20, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, 500));
        TaskDispatchEvidenceView evidence = evidenceService.evidence(taskId, safeLimit);
        TaskExecutionJourneyView journey = journeyService.journey(taskId);
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
        steps.add(stepFromJourney("TEST_TASK_CREATED", "Task Created", journeyStage(journey, TaskExecutionJourneyStageCode.INTAKE), "Task intake evidence is not available yet."));
        steps.add(stepFromJourney("ROUTING_SELECTED_AGENT", "Routing Selected Agent", journeyStage(journey, TaskExecutionJourneyStageCode.ROUTING), "Routing decision evidence is not available yet."));
        steps.add(dispatchRequestCreatedStep(latest));
        steps.add(stepFromJourney("RUNTIME_DELIVERED", "Runtime Delivered", journeyStage(journey, TaskExecutionJourneyStageCode.DELIVERY), "Runtime delivery evidence is not available yet."));
        steps.add(stepFromJourney("AGENT_ACK", "Agent ACK", journeyStage(journey, TaskExecutionJourneyStageCode.ACK), "Waiting for accepted Agent ACK evidence."));
        steps.add(stepFromJourney("AGENT_RESULT", "Agent RESULT / ERROR", journeyStage(journey, TaskExecutionJourneyStageCode.RESULT), "Waiting for accepted Agent RESULT/ERROR evidence."));
        steps.add(callbackInboxJourneyStep(journey));
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
        diagnostics.put("authority", "V24_TASK_EXECUTION_JOURNEY_V1");
        diagnostics.put("journeyRevision", journey.revision());
        diagnostics.put("journeyStatus", journey.status().name());
        diagnostics.put("journeyCurrentStage", journey.currentStage().name());
        diagnostics.put("timeoutSeconds", safeTimeout);
        diagnostics.put("elapsedSeconds", elapsedSeconds);
        diagnostics.put("latestDispatchStatus", latest == null || latest.getStatus() == null ? null : latest.getStatus().name());
        diagnostics.put("evidenceStatus", evidence.getStatus());
        view.setDiagnostics(diagnostics);
        return view;
    }

    private TaskRuntimeVerificationStep stepFromJourney(String stepCode, String title, TaskExecutionJourneyStage source, String fallback) {
        if (source == null) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of(stepCode, "PENDING", title, fallback);
            step.setNextAction(nextActionForStep(stepCode));
            return step;
        }
        String status = switch (source.status()) {
            case SUCCEEDED, NOT_APPLICABLE -> "PASS";
            case BLOCKED, FAILED_RETRYABLE, FAILED_FINAL, CONFLICT -> "BLOCKED";
            case NOT_STARTED, PENDING, IN_PROGRESS -> "PENDING";
        };
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of(stepCode, status, title, firstNonBlank(source.summary(), fallback));
        step.setObservedAt(first(source.completedAt(), source.lastChangedAt(), source.startedAt()));
        if ("BLOCKED".equals(status)) step.setBlockingCode(firstNonBlank(source.reasonCode(), "JOURNEY_STAGE_BLOCKED"));
        if (!"PASS".equals(status)) step.setNextAction(nextActionForStep(stepCode));
        step.setDetails(details(
                "journeyStage", source.stage().name(),
                "journeyStatus", source.status().name(),
                "reasonCode", source.reasonCode(),
                "authority", source.authority(),
                "revision", source.revision(),
                "retryability", source.retryability().name(),
                "retryAfter", source.retryAfter(),
                "evidenceRefs", source.evidenceRefs()));
        return step;
    }

    private TaskRuntimeVerificationStep callbackInboxJourneyStep(TaskExecutionJourneyView journey) {
        TaskExecutionJourneyStage ack = journeyStage(journey, TaskExecutionJourneyStageCode.ACK);
        TaskExecutionJourneyStage result = journeyStage(journey, TaskExecutionJourneyStageCode.RESULT);
        boolean ackPresent = hasCallbackEvidence(ack);
        boolean resultPresent = hasCallbackEvidence(result);
        TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("CALLBACK_INBOX", ackPresent || resultPresent ? "PASS" : "PENDING",
                "Callback Inbox", ackPresent || resultPresent ? "Accepted callback evidence is referenced by the canonical TaskExecutionJourney." : "Waiting for accepted callback evidence.");
        if (!ackPresent && !resultPresent) step.setNextAction("Open Callback Inbox");
        step.setObservedAt(first(result == null ? null : result.lastChangedAt(), ack == null ? null : ack.lastChangedAt()));
        step.setDetails(details("authority", "V24_TASK_EXECUTION_JOURNEY_V1", "ackEvidenceRefs", ack == null ? List.of() : ack.evidenceRefs(),
                "resultEvidenceRefs", result == null ? List.of() : result.evidenceRefs()));
        return step;
    }


    private boolean hasCallbackEvidence(TaskExecutionJourneyStage stage) {
        return stage != null && stage.evidenceRefs() != null && stage.evidenceRefs().stream()
                .anyMatch(ref -> ref != null && "TASK_CALLBACK".equals(ref.evidenceType()));
    }

    private TaskExecutionJourneyStage journeyStage(TaskExecutionJourneyView journey, TaskExecutionJourneyStageCode code) {
        if (journey == null || journey.stages() == null) return null;
        return journey.stages().stream().filter(stage -> stage.stage() == code).findFirst().orElse(null);
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

    private TaskRuntimeVerificationStep taskTerminalStep(TaskRecord task, DispatchRequest latest) {
        boolean terminal = task != null && task.getStatus() != null && task.getStatus().isTerminal();
        if (terminal) {
            TaskRuntimeVerificationStep step = TaskRuntimeVerificationStep.of("TASK_COMPLETED", "PASS", "Task Completed", "Authoritative TaskRecord is in a terminal state.");
            step.setObservedAt(first(task.getTerminalAt(), task.getUpdatedAt()));
            step.setDetails(details("authority", "TaskRecord", "taskStatus", task.getStatus(), "dispatchStatus", latest == null ? null : latest.getStatus()));
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
