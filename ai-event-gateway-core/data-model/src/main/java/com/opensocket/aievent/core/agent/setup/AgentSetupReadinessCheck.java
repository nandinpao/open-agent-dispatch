package com.opensocket.aievent.core.agent.setup;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSetupReadinessCheck {
    private String code;
    private String label;
    private String status;
    private boolean ready;
    private String description;
    private String action;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public static AgentSetupReadinessCheck ready(String code, String label, String description) {
        AgentSetupReadinessCheck check = new AgentSetupReadinessCheck();
        check.setCode(code);
        check.setLabel(label);
        check.setStatus("READY");
        check.setReady(true);
        check.setDescription(description);
        return check;
    }

    public static AgentSetupReadinessCheck pending(String code, String label, String description, String action) {
        AgentSetupReadinessCheck check = new AgentSetupReadinessCheck();
        check.setCode(code);
        check.setLabel(label);
        check.setStatus("PENDING");
        check.setReady(false);
        check.setDescription(description);
        check.setAction(action);
        return check;
    }
}
