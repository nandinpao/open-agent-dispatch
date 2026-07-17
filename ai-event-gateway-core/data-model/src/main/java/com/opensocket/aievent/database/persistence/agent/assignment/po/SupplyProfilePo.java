package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SupplyProfilePo {
    private String tenantId;
    private String supplyProfileId;
    private String profileCode;
    private String profileName;
    private String agentId;
    private String runtimeBindingId;
    private String runtimeId;
    private String serviceRole;
    private String serviceLevel;
    private String qualityGrade;
    private String riskLimit;
    private String dataScope;
    private String capacityPolicy;
    private String status;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime expiresAt;
    private String capabilitySnapshotJson;
    private String runtimeFeatureSnapshotJson;
    private String qualitySnapshotJson;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
