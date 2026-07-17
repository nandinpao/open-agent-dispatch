package com.opensocket.aievent.core.agent.assignment;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchTaskDefinitionReviewCommand {
    private String operatorId;
    private String reason;
    private String targetDefinitionId;
    private boolean force;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
