package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DispatchTaskDefinitionPo {
    private String tenantId;
    private String definitionId;
    private String sourceSystem;
    private String taskType;
    private String displayName;
    private String description;
    private String domain;
    private String riskLevel;
    private String defaultSeverity;
    private String ownerTeam;
    private String status;
    private int version;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
