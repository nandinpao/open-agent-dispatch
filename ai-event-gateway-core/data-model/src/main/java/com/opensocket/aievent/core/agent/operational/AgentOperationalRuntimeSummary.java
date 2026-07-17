package com.opensocket.aievent.core.agent.operational;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentOperationalRuntimeSummary {
    private String status;
    private String connectionStatus = "UNKNOWN";
    private boolean online;
    private boolean assignable;
    private boolean draining;
    private int currentTaskCount;
    private int reservedTaskCount;
    private int maxConcurrentTasks;
    private int availableSlots;
    private double capacityUtilization;
    private int runtimeFailureCount;
    private OffsetDateTime lastHeartbeatAt;
    private OffsetDateTime leaseExpiresAt;
    private OffsetDateTime runtimeBackoffUntil;
    private String runtimeBackoffReason;
    private String ownerGatewayNodeId;
    private String agentSessionId;
}
