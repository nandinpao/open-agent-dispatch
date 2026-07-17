package com.opensocket.aievent.core.agent.operational;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentOperationalAuthorityCheck {
    private String code;
    private String label;
    private String status = "INFO";
    private boolean ready;
    private boolean blocking;
    private String message;
    private String source;
    private String category;
    private AgentOperationalNextAction nextAction;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public static AgentOperationalAuthorityCheck of(String category,
                                                    String code,
                                                    String label,
                                                    boolean ready,
                                                    boolean blocking,
                                                    String message,
                                                    String source) {
        AgentOperationalAuthorityCheck check = new AgentOperationalAuthorityCheck();
        check.setCategory(category);
        check.setCode(code);
        check.setLabel(label);
        check.setReady(ready);
        check.setBlocking(blocking);
        check.setStatus(ready ? "PASS" : blocking ? "BLOCKED" : "WARN");
        check.setMessage(message);
        check.setSource(source);
        return check;
    }

    public AgentOperationalAuthorityCheck withMetadata(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            metadata.put(key, value);
        }
        return this;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
