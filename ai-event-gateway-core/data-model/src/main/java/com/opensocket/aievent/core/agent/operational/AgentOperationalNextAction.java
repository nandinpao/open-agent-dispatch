package com.opensocket.aievent.core.agent.operational;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentOperationalNextAction {
    private String code;
    private String label;
    private String target;
    private String severity = "INFO";
    private Map<String, Object> payload = new LinkedHashMap<>();

    public static AgentOperationalNextAction of(String code, String label, String target, String severity) {
        AgentOperationalNextAction action = new AgentOperationalNextAction();
        action.setCode(code);
        action.setLabel(label);
        action.setTarget(target);
        action.setSeverity(severity);
        return action;
    }

    public AgentOperationalNextAction withPayload(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            payload.put(key, value);
        }
        return this;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }
}
