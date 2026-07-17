package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentCapabilityCommand {
    private String tenantId;
    private String capabilityCode;
    private String operatorId;
    private String source;
    private String evidenceRef;
    private OffsetDateTime expiresAt;
    private String reason;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
