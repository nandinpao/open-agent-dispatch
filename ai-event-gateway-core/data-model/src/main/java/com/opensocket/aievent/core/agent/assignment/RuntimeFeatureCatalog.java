package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuntimeFeatureCatalog {
    private String tenantId;
    private String featureId;
    private String featureCode;
    private String featureName;
    private String category = "RUNTIME";
    private String description;
    private String status = "ACTIVE";
    private int version = 1;
    private boolean requiresProbe = true;
    private boolean requiresTrustApproval = true;
    private boolean dispatchEligible = true;
    private String ownerTeam;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime retiredAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
