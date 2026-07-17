package com.opensocket.aievent.core.agent.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractReadinessRequest {
    private String tenantId;
    private String sourceSystem;
    private String taskType;
    private String agentId;
    private List<String> requiredCapabilities = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
