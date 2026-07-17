package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeFeatureCommand {
    private String tenantId;
    private String featureCode;
    private String operatorId;
    private String source = "ADMIN_UI";
    private String observedValue = "true";
    private String probeResult;
    private String evidenceRef;
    private OffsetDateTime expiresAt;
    private String reason;
    private String confirmationPhrase;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
