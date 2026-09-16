package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Legacy Phase 9D Agent Pool Capability Policy DTO.
 *
 * <p>V38 retires this object as a mutable routing authority. It remains only so legacy
 * GET/PUT API signatures can return an empty projection / HTTP 410 without breaking
 * binary/source compatibility. Canonical routing uses Task requiredCapabilities.</p>
 */
@Getter
@Setter
public class AgentPoolCapabilityPolicy {
    private String tenantId;
    private String policyId;
    private String targetPoolId;
    private String targetPoolName;
    private List<String> requiredCapabilities = List.of();
    private String matchMode = "ANY";
    private String enforcementMode = "ADVISORY";
    private boolean advisoryOnly = true;
    private boolean explicitPoolPolicy = true;
    private boolean routingGate = false;
    private String riskLevel = "LOW";
    private String riskWarning;
    private Map<String, Object> simulationImpact = new LinkedHashMap<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String createdBy;
    private String updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }

    public void setSimulationImpact(Map<String, Object> simulationImpact) {
        this.simulationImpact = simulationImpact == null ? new LinkedHashMap<>() : new LinkedHashMap<>(simulationImpact);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
