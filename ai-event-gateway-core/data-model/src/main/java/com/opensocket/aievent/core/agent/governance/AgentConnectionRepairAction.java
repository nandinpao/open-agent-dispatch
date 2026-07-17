package com.opensocket.aievent.core.agent.governance;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentConnectionRepairAction {
    private String actionCode;
    private String label;
    private String description;
    private String actionType = "EXECUTE";
    private String method = "POST";
    private String endpoint;
    private boolean enabled = true;
    private boolean requiresCredentialToken;
    private boolean highRisk;
    private String disabledReason;
    private String nextStep;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public AgentConnectionRepairAction() {}

    public AgentConnectionRepairAction(String actionCode, String label, String description) {
        this.actionCode = actionCode;
        this.label = label;
        this.description = description;
    }

    public static AgentConnectionRepairAction execute(String actionCode, String label, String description, String endpoint) {
        AgentConnectionRepairAction action = new AgentConnectionRepairAction(actionCode, label, description);
        action.setEndpoint(endpoint);
        action.setActionType("EXECUTE");
        action.setMethod("POST");
        return action;
    }

    public static AgentConnectionRepairAction navigate(String actionCode, String label, String description, String endpoint) {
        AgentConnectionRepairAction action = new AgentConnectionRepairAction(actionCode, label, description);
        action.setEndpoint(endpoint);
        action.setActionType("NAVIGATE");
        action.setMethod("GET");
        return action;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
