package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeCapabilityProfile {
    private String agentId;
    private String agentType;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String pluginName;
    private String pluginVersion;
    private String capabilityRevision;
    private String executorMode;
    private String placementPool;
    private String placementRegion;
    private String placementZone;
    private int maxConcurrentTasks = 1;
    private Map<String, Object> capabilityProfile = new LinkedHashMap<>();
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;

    public void setCapabilityProfile(Map<String, Object> capabilityProfile) {
        this.capabilityProfile = capabilityProfile == null ? new LinkedHashMap<>() : new LinkedHashMap<>(capabilityProfile);
    }

    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = Math.max(1, maxConcurrentTasks);
    }
}
