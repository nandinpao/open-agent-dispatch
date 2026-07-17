package com.opensocket.aievent.core.agent.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractBootstrapRequest {
    private String tenantId;
    private String sourceSystem;
    private String sourceSystemName;
    private String taskType;
    private String displayName;
    private String description;
    private String domain;
    private String riskLevel = "MIDDLE";
    private String defaultSeverity = "MIDDLE";
    private String ownerTeam;
    private String capabilityCode;
    private String capabilityName;
    private String profileCode;
    private String profileName;
    private String policyCode;
    private String policyName;
    private String toolPolicy = "PROPOSE_ONLY";
    private String objectType;
    private String eventType;
    private String errorCode;
    private String messagePattern;
    private Integer eventMappingPriority = 100;
    private List<String> requiredRuntimeFeatures = new ArrayList<>();
    private String agentId;
    private boolean assignAgent;
    private boolean approveAgentQualification = true;
    private boolean approveAgentCapability = true;
    private boolean activate = true;
    private String operatorId;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setRequiredRuntimeFeatures(List<String> requiredRuntimeFeatures) {
        this.requiredRuntimeFeatures = requiredRuntimeFeatures == null ? new ArrayList<>() : new ArrayList<>(requiredRuntimeFeatures);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
