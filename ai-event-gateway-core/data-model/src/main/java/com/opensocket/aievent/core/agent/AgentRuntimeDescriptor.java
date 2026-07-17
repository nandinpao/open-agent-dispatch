package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeDescriptor {
    private String agentId;
    private String agentType;
    private String pluginName;
    private String pluginVersion;
    private String protocolVersion;
    private String connectionType;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String siteId;
    private String region;
    private String zone;
    private AgentStatus status = AgentStatus.IDLE;
    private List<String> runtimeFeatures = List.of();
    private int activeTasks;
    private int maxConcurrentTasks = 1;
    private int availableSlots;
    private double capacityUtilization;
    private boolean draining;
    private long heartbeatSequence;
    private OffsetDateTime connectedAt;
    private OffsetDateTime lastHeartbeatAt;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime updatedAt;
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    public void setRuntimeFeatures(List<String> runtimeFeatures) {
        this.runtimeFeatures = runtimeFeatures == null ? List.of() : List.copyOf(runtimeFeatures);
    }

    public void setActiveTasks(int activeTasks) { this.activeTasks = Math.max(0, activeTasks); }
    public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = Math.max(1, maxConcurrentTasks); }
    public void setAvailableSlots(int availableSlots) { this.availableSlots = Math.max(0, availableSlots); }
    public void setCapacityUtilization(double capacityUtilization) { this.capacityUtilization = Math.max(0.0d, Math.min(1.0d, capacityUtilization)); }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawPayload);
    }
}
