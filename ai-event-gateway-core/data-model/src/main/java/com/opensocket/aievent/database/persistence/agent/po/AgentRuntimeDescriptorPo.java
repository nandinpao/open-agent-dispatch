package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeDescriptorPo {
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
    private String status;
    private String runtimeFeaturesJson;
    private int activeTasks;
    private int maxConcurrentTasks;
    private int availableSlots;
    private double capacityUtilization;
    private boolean draining;
    private long heartbeatSequence;
    private OffsetDateTime connectedAt;
    private OffsetDateTime lastHeartbeatAt;
    private OffsetDateTime lastSeenAt;
    private String rawPayloadJson;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime updatedAt;
}
