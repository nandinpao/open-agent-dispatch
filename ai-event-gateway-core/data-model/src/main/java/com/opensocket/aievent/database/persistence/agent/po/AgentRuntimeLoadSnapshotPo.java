package com.opensocket.aievent.database.persistence.agent.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentRuntimeLoadSnapshotPo {
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String status;
    private int activeTasks;
    private int maxConcurrentTasks;
    private int availableSlots;
    private double capacityUtilization;
    private int outboxPending;
    private int outboxInFlight;
    private int recoveryPendingAssignments;
    private boolean draining;
    private long heartbeatSequence;
    private String runtimeLoadJson;
    private OffsetDateTime heartbeatAt;
    private OffsetDateTime updatedAt;
}
