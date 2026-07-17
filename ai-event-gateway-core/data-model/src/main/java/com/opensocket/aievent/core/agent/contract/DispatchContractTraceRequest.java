package com.opensocket.aievent.core.agent.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractTraceRequest {
    private String tenantId;
    private String taskId;
    private String sourceSystem;
    private String taskType;
    private String agentId;
    private String domain;
    private String objectType;
    private String eventType;
    private String errorCode;
    private String message;
    private List<String> requiredCapabilities = new ArrayList<>();
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities);
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }
}
