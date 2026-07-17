package com.opensocket.aievent.core.agent.setup;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSetupTroubleshootingStep {
    private String code;
    private String label;
    private String severity = "INFO";
    private String description;
    private String action;
    private String command;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public static AgentSetupTroubleshootingStep info(String code, String label, String description, String action) {
        return of(code, label, "INFO", description, action, null);
    }

    public static AgentSetupTroubleshootingStep warn(String code, String label, String description, String action) {
        return of(code, label, "WARN", description, action, null);
    }

    public static AgentSetupTroubleshootingStep error(String code, String label, String description, String action) {
        return of(code, label, "ERROR", description, action, null);
    }

    public static AgentSetupTroubleshootingStep command(String code, String label, String description, String command) {
        return of(code, label, "INFO", description, null, command);
    }

    private static AgentSetupTroubleshootingStep of(String code, String label, String severity, String description, String action, String command) {
        AgentSetupTroubleshootingStep step = new AgentSetupTroubleshootingStep();
        step.setCode(code);
        step.setLabel(label);
        step.setSeverity(severity);
        step.setDescription(description);
        step.setAction(action);
        step.setCommand(command);
        return step;
    }
}
