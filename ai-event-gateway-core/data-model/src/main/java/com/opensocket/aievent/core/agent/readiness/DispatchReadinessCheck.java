package com.opensocket.aievent.core.agent.readiness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DispatchReadinessCheck {
    private String key;
    private String label;
    private DispatchReadinessStatus status = DispatchReadinessStatus.INFO;
    private String message;
    private String beginnerHint;
    private List<String> evidence = new ArrayList<>();
    private DispatchReadinessFixAction fixAction;
    private Map<String, Object> details = new LinkedHashMap<>();

    public DispatchReadinessCheck() {}

    public DispatchReadinessCheck(String key, String label, DispatchReadinessStatus status, String message) {
        this.key = key;
        this.label = label;
        this.status = status;
        this.message = message;
    }

    public static DispatchReadinessCheck pass(String key, String label, String message) {
        return new DispatchReadinessCheck(key, label, DispatchReadinessStatus.PASS, message);
    }

    public static DispatchReadinessCheck fail(String key, String label, String message) {
        return new DispatchReadinessCheck(key, label, DispatchReadinessStatus.FAIL, message);
    }

    public static DispatchReadinessCheck warn(String key, String label, String message) {
        return new DispatchReadinessCheck(key, label, DispatchReadinessStatus.WARN, message);
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public DispatchReadinessStatus getStatus() { return status; }
    public void setStatus(DispatchReadinessStatus status) { this.status = status == null ? DispatchReadinessStatus.INFO : status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getBeginnerHint() { return beginnerHint; }
    public void setBeginnerHint(String beginnerHint) { this.beginnerHint = beginnerHint; }
    public List<String> getEvidence() { return evidence; }
    public void setEvidence(List<String> evidence) { this.evidence = evidence == null ? new ArrayList<>() : new ArrayList<>(evidence); }
    public DispatchReadinessFixAction getFixAction() { return fixAction; }
    public void setFixAction(DispatchReadinessFixAction fixAction) { this.fixAction = fixAction; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details); }
}
