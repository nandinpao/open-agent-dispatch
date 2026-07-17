package com.opensocket.aievent.core.agent.readiness;

import java.util.LinkedHashMap;
import java.util.Map;

public class DispatchReadinessFixAction {
    private String label;
    private String actionType;
    private String targetPath;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public DispatchReadinessFixAction() {}

    public DispatchReadinessFixAction(String label, String actionType, String targetPath) {
        this.label = label;
        this.actionType = actionType;
        this.targetPath = targetPath;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload); }
}
