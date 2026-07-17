package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentRuntimeLoadSnapshot {
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private AgentStatus status = AgentStatus.IDLE;
    private int activeTasks;
    private int maxConcurrentTasks = 1;
    private int availableSlots;
    private double capacityUtilization;
    private int outboxPending;
    private int outboxInFlight;
    private int recoveryPendingAssignments;
    private boolean draining;
    private long heartbeatSequence;
    private Map<String, Object> runtimeLoad = new LinkedHashMap<>();
    private OffsetDateTime heartbeatAt;
    private OffsetDateTime updatedAt;

    public void setActiveTasks(int activeTasks) { this.activeTasks = Math.max(0, activeTasks); }
    public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = Math.max(1, maxConcurrentTasks); }
    public void setAvailableSlots(int availableSlots) { this.availableSlots = Math.max(0, availableSlots); }
    public void setCapacityUtilization(double capacityUtilization) { this.capacityUtilization = Math.max(0.0d, Math.min(1.0d, capacityUtilization)); }
    public void setOutboxPending(int outboxPending) { this.outboxPending = Math.max(0, outboxPending); }
    public void setOutboxInFlight(int outboxInFlight) { this.outboxInFlight = Math.max(0, outboxInFlight); }
    public void setRecoveryPendingAssignments(int recoveryPendingAssignments) { this.recoveryPendingAssignments = Math.max(0, recoveryPendingAssignments); }
    public void setRuntimeLoad(Map<String, Object> runtimeLoad) { this.runtimeLoad = runtimeLoad == null ? new LinkedHashMap<>() : new LinkedHashMap<>(runtimeLoad); }
}
