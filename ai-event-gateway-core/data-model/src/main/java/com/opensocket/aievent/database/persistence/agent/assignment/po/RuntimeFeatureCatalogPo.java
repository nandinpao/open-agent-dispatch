package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RuntimeFeatureCatalogPo {
    private String tenantId;
    private String featureId;
    private String featureCode;
    private String featureName;
    private String category;
    private String description;
    private String status;
    private int version;
    private boolean requiresProbe;
    private boolean requiresTrustApproval;
    private boolean dispatchEligible;
    private String ownerTeam;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
