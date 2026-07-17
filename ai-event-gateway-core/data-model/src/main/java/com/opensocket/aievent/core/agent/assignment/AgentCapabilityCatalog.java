package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentCapabilityCatalog {
    private String tenantId;
    private String capabilityId;
    private String capabilityCode;
    private String capabilityName;
    private String category;
    private String capabilityType = "SERVICE";
    private String domain;
    private String resourceType;
    private String operation;
    private String dataClass;
    private String serviceLevel;
    private boolean legacyTaskCoupling;
    private String migrationStatus = "CURRENT";
    private String description;
    private String taskDefinitionId;
    private String sourceSystem;
    private String taskType;
    private String riskLevel = "MIDDLE";
    private String status = "ACTIVE";
    private int version = 1;
    private String ownerTeam;
    private boolean requiresApproval = true;
    private boolean requiresCertification;
    private boolean requiresRuntimeProbe;
    private boolean dispatchEligible = true;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
