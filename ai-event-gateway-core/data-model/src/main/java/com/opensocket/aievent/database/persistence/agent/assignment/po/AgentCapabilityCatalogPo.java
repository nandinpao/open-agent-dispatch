package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentCapabilityCatalogPo {
    private String tenantId;
    private String capabilityId;
    private String capabilityCode;
    private String capabilityName;
    private String category;
    private String capabilityType;
    private String domain;
    private String resourceType;
    private String operation;
    private String dataClass;
    private String serviceLevel;
    private boolean legacyTaskCoupling;
    private String migrationStatus;
    private String description;
    private String taskDefinitionId;
    private String sourceSystem;
    private String taskType;
    private String riskLevel;
    private String status;
    private int version;
    private String ownerTeam;
    private boolean requiresApproval;
    private boolean requiresCertification;
    private boolean requiresRuntimeProbe;
    private boolean dispatchEligible;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
