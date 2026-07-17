package com.opensocket.aievent.core.agent.setup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSetupRequest {
    private String tenantId;
    private String agentId;
    private String agentName;
    private String ownerTeam;
    private String description;
    private String purpose;
    private String runtimeType = "Docker";
    private String gatewayUrl = "http://localhost:18081";
    private String credentialToken;
    private boolean autoApprove = false;
    private boolean createDefaultCapabilities = false;
    private boolean createRuntimeBinding = false;
    private boolean createSupplyProfile = false;
    private boolean createDefaultDispatchRule = false;
    private int capacityLimit = 1;
    private String operatorId = "admin-ui";
    private List<String> defaultCapabilities = List.of();
    private List<String> defaultTaskTypes = List.of();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setDefaultCapabilities(List<String> defaultCapabilities) {
        this.defaultCapabilities = defaultCapabilities == null ? List.of() : List.copyOf(defaultCapabilities);
    }

    public void setDefaultTaskTypes(List<String> defaultTaskTypes) {
        this.defaultTaskTypes = defaultTaskTypes == null ? List.of() : List.copyOf(defaultTaskTypes);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
