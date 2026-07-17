package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchTaskDefinition {
    private String tenantId;
    private String definitionId;
    private String sourceSystem;
    private String taskType;
    private String displayName;
    private String description;
    private String domain;
    private String riskLevel = "MIDDLE";
    private String defaultSeverity = "MIDDLE";
    private String ownerTeam;
    private String status = "ACTIVE";
    private int version = 1;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
