package com.opensocket.aievent.core.agent.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuntimeResource {
    private String tenantId;
    private String runtimeId;
    private String runtimeCode;
    private String runtimeName;
    private String runtimeType = "RUNTIME";
    private String connectorType;
    private String executionHost;
    private String environment = "default";
    private String region;
    private String zone;
    private String trustStatus = "UNVERIFIED";
    private String status = "REGISTERED";
    private int capacityLimit = 1;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
