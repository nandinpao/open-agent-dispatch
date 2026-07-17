package com.opensocket.aievent.core.task.evidence;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P6 runtime E2E verification read model.
 *
 * <p>This model answers the operator question: after a test task was created, how far did the
 * dispatch execution really get through Core routing, runtime delivery, Agent ACK/RESULT,
 * callback inbox and task terminal state?</p>
 */
public class TaskRuntimeVerificationView {
    private String taskId;
    private String status;
    private String summary;
    private String currentStep;
    private String firstBlockingStep;
    private String firstBlockingCode;
    private String firstBlockingReason;
    private boolean timedOut;
    private int timeoutSeconds;
    private long elapsedSeconds;
    private String selectedAgentId;
    private String dispatchRequestId;
    private List<TaskRuntimeVerificationStep> steps = List.of();
    private TaskDispatchEvidenceView evidence;
    private List<TaskDispatchRecoveryAction> suggestedActions = List.of();
    private Map<String, Object> diagnostics = Map.of();
    private OffsetDateTime startedAt;
    private OffsetDateTime generatedAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public String getFirstBlockingStep() { return firstBlockingStep; }
    public void setFirstBlockingStep(String firstBlockingStep) { this.firstBlockingStep = firstBlockingStep; }
    public String getFirstBlockingCode() { return firstBlockingCode; }
    public void setFirstBlockingCode(String firstBlockingCode) { this.firstBlockingCode = firstBlockingCode; }
    public String getFirstBlockingReason() { return firstBlockingReason; }
    public void setFirstBlockingReason(String firstBlockingReason) { this.firstBlockingReason = firstBlockingReason; }
    public boolean isTimedOut() { return timedOut; }
    public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = Math.max(1, timeoutSeconds); }
    public long getElapsedSeconds() { return elapsedSeconds; }
    public void setElapsedSeconds(long elapsedSeconds) { this.elapsedSeconds = Math.max(0L, elapsedSeconds); }
    public String getSelectedAgentId() { return selectedAgentId; }
    public void setSelectedAgentId(String selectedAgentId) { this.selectedAgentId = selectedAgentId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public List<TaskRuntimeVerificationStep> getSteps() { return steps; }
    public void setSteps(List<TaskRuntimeVerificationStep> steps) { this.steps = steps == null ? List.of() : List.copyOf(steps); }
    public TaskDispatchEvidenceView getEvidence() { return evidence; }
    public void setEvidence(TaskDispatchEvidenceView evidence) { this.evidence = evidence; }
    public List<TaskDispatchRecoveryAction> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<TaskDispatchRecoveryAction> suggestedActions) { this.suggestedActions = suggestedActions == null ? List.of() : List.copyOf(suggestedActions); }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(Map<String, Object> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            this.diagnostics = Map.of();
            return;
        }
        // Map.copyOf throws NullPointerException when a diagnostic value is null.
        // Runtime verification diagnostics legitimately include nullable fields such
        // as latestDispatchStatus before the dispatch request exists, so preserve
        // them in a defensive mutable-free copy instead of failing the endpoint.
        this.diagnostics = new LinkedHashMap<>(diagnostics);
    }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
