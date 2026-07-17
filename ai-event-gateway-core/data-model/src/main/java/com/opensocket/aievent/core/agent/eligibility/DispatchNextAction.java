package com.opensocket.aievent.core.agent.eligibility;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchNextAction {
    private String action;
    private String label;
    private String severity = "INFO";
    private Map<String, Object> payload = new LinkedHashMap<>();

    public static DispatchNextAction of(String action, String label) {
        DispatchNextAction nextAction = new DispatchNextAction();
        nextAction.setAction(action);
        nextAction.setLabel(label);
        return nextAction;
    }

    public static DispatchNextAction of(String action, String label, String severity) {
        DispatchNextAction nextAction = of(action, label);
        nextAction.setSeverity(severity);
        return nextAction;
    }

    public DispatchNextAction withPayload(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            this.payload.put(key, value);
        }
        return this;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }
}
