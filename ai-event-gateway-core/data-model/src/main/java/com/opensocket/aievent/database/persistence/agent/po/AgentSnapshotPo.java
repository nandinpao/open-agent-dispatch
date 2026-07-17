package com.opensocket.aievent.database.persistence.agent.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AgentSnapshotPo {
    private String agentId;
    private String agentType;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String siteId;
    private String siteName;
    private String region;
    private String zone;
    private String status;
    private String capabilitiesJson;
    private int currentTaskCount;
    private int reservedTaskCount;
    private int maxConcurrentTasks;
    private int healthScore;
    private String capabilityProfileJson;
    private String runtimeLoadJson;
    private String pluginName;
    private String pluginVersion;
    private String capabilityRevision;
    private int availableSlots;
    private double capacityUtilization;
    private int outboxPending;
    private int outboxInFlight;
    private int recoveryPendingAssignments;
    private boolean draining;
    private OffsetDateTime connectedAt;
    private OffsetDateTime lastHeartbeatAt;
    private OffsetDateTime disconnectedAt;
    private OffsetDateTime leaseExpiresAt;
    private OffsetDateTime runtimeBackoffUntil;
    private String runtimeBackoffReason;
    private int runtimeFailureCount;
}
