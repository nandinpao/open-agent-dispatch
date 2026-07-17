package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeCapabilityProfilePo {
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
    private int maxConcurrentTasks;
    private String capabilityProfileJson;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;
}
