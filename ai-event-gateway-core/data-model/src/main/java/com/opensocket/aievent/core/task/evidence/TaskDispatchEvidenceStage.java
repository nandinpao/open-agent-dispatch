package com.opensocket.aievent.core.task.evidence;

import java.util.LinkedHashMap;
import java.util.Map;

public class TaskDispatchEvidenceStage {
    private String stage;
    private String status;
    private String title;
    private String summary;
    private String blockingCode;
    private String nextAction;
    private Map<String, Object> details = Map.of();

    public TaskDispatchEvidenceStage() {}

    public TaskDispatchEvidenceStage(String stage, String status, String title, String summary, String blockingCode, String nextAction, Map<String, Object> details) {
        this.stage = stage;
        this.status = status;
        this.title = title;
        this.summary = summary;
        this.blockingCode = blockingCode;
        this.nextAction = nextAction;
        setDetails(details);
    }

    public static TaskDispatchEvidenceStage of(String stage, String status, String title, String summary) {
        return new TaskDispatchEvidenceStage(stage, status, title, summary, null, null, Map.of());
    }

    public static TaskDispatchEvidenceStage blocked(String stage, String title, String summary, String blockingCode, String nextAction) {
        return new TaskDispatchEvidenceStage(stage, "BLOCKED", title, summary, blockingCode, nextAction, Map.of());
    }

    public TaskDispatchEvidenceStage withDetail(String key, Object value) {
        if (key == null || value == null) return this;
        Map<String, Object> copy = new LinkedHashMap<>(details == null ? Map.of() : details);
        copy.put(key, value);
        this.details = copy;
        return this;
    }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
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
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details == null ? Map.of() : Map.copyOf(details); }
}
