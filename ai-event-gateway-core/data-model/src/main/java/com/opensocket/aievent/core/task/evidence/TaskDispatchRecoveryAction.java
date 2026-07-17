package com.opensocket.aievent.core.task.evidence;

import java.util.Map;

public class TaskDispatchRecoveryAction {
    private String action;
    private String label;
    private String description;
    private String endpoint;
    private String method;
    private String riskLevel;
    private boolean enabled;
    private Map<String, Object> payload = Map.of();

    public TaskDispatchRecoveryAction() {}

    public TaskDispatchRecoveryAction(String action, String label, String description, String endpoint, String method, String riskLevel, boolean enabled, Map<String, Object> payload) {
        this.action = action;
        this.label = label;
        this.description = description;
        this.endpoint = endpoint;
        this.method = method;
        this.riskLevel = riskLevel;
        this.enabled = enabled;
        setPayload(payload);
    }

    public static TaskDispatchRecoveryAction of(String action, String label, String description, String endpoint, String method, String riskLevel, boolean enabled) {
        return new TaskDispatchRecoveryAction(action, label, description, endpoint, method, riskLevel, enabled, Map.of());
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? Map.of() : Map.copyOf(payload); }
}
