package com.opensocket.aievent.core.task.evidence;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Operator-facing runtime E2E verification step.
 *
 * <p>P6 keeps this as a read model: it does not mutate dispatch state. Recovery commands
 * are exposed separately as suggested actions so the UI can make operator intent explicit.</p>
 */
public class TaskRuntimeVerificationStep {
    private String step;
    private String status;
    private String title;
    private String summary;
    private String blockingCode;
    private String nextAction;
    private OffsetDateTime observedAt;
    private Map<String, Object> details = Map.of();

    public TaskRuntimeVerificationStep() {}

    public TaskRuntimeVerificationStep(String step, String status, String title, String summary) {
        this.step = step;
        this.status = status;
        this.title = title;
        this.summary = summary;
    }

    public static TaskRuntimeVerificationStep of(String step, String status, String title, String summary) {
        return new TaskRuntimeVerificationStep(step, status, title, summary);
    }

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBlockingCode() { return blockingCode; }
    public void setBlockingCode(String blockingCode) { this.blockingCode = blockingCode; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public OffsetDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(OffsetDateTime observedAt) { this.observedAt = observedAt; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details == null ? Map.of() : Map.copyOf(details); }
}
