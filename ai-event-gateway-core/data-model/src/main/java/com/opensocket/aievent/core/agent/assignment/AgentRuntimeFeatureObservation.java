package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeFeatureObservation {
    private String tenantId;
    private String observationId;
    private String agentId;
    private String featureCode;
    private String featureName;
    private String observedValue = "true";
    private String source = "HEARTBEAT";
    private String probeResult = "OBSERVED";
    private OffsetDateTime observedAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
