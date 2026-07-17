package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplyProfile {
    private String tenantId;
    private String supplyProfileId;
    private String profileCode;
    private String profileName;
    private String agentId;
    private String runtimeBindingId;
    private String runtimeId;
    private String serviceRole = "GENERAL_SUPPLY";
    private String serviceLevel = "STANDARD";
    private String qualityGrade = "UNKNOWN";
    private String riskLimit = "MIDDLE";
    private String dataScope = "STANDARD";
    private String capacityPolicy = "DEFAULT";
    private String status = "DRAFT";
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime expiresAt;
    private List<String> capabilitySnapshot = List.of();
    private List<String> runtimeFeatureSnapshot = List.of();
    private Map<String, Object> qualitySnapshot = new LinkedHashMap<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setCapabilitySnapshot(List<String> capabilitySnapshot) {
        this.capabilitySnapshot = capabilitySnapshot == null ? List.of() : List.copyOf(capabilitySnapshot);
    }

    public void setRuntimeFeatureSnapshot(List<String> runtimeFeatureSnapshot) {
        this.runtimeFeatureSnapshot = runtimeFeatureSnapshot == null ? List.of() : List.copyOf(runtimeFeatureSnapshot);
    }

    public void setQualitySnapshot(Map<String, Object> qualitySnapshot) {
        this.qualitySnapshot = qualitySnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(qualitySnapshot);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public boolean activeAt(OffsetDateTime now) {
        OffsetDateTime current = now == null ? OffsetDateTime.now() : now;
        boolean statusActive = "ACTIVE".equalsIgnoreCase(status);
        boolean effective = effectiveFrom == null || !effectiveFrom.isAfter(current);
        boolean notExpired = expiresAt == null || expiresAt.isAfter(current);
        return statusActive && effective && notExpired;
    }
}
