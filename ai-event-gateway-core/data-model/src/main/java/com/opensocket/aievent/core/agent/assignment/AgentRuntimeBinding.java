package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeBinding {
    private String tenantId;
    private String bindingId;
    private String agentId;
    private String runtimeId;
    private String runtimeCode;
    private String bindingStatus = "PENDING";
    private String verifiedBy;
    private OffsetDateTime verifiedAt;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime expiresAt;
    private int capacityLimit = 1;
    private String region;
    private String zone;
    private String dataScope = "STANDARD";
    private String riskLimit = "MIDDLE";
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public boolean activeAt(OffsetDateTime now) {
        OffsetDateTime current = now == null ? OffsetDateTime.now() : now;
        boolean statusActive = "ACTIVE".equalsIgnoreCase(bindingStatus);
        boolean effective = effectiveFrom == null || !effectiveFrom.isAfter(current);
        boolean notExpired = expiresAt == null || expiresAt.isAfter(current);
        return statusActive && effective && notExpired;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
