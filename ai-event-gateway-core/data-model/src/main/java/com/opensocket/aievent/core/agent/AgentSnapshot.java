package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class AgentSnapshot {
    @ToString.Include
    private String agentId;
    private String agentType;
    @ToString.Include
    private String ownerGatewayNodeId;
    @ToString.Include
    private String agentSessionId;
    @ToString.Include
    private String siteId;
    private String siteName;
    private String region;
    private String zone;
    private AgentStatus status = AgentStatus.IDLE;
    private List<String> capabilities = List.of();
    private int currentTaskCount;
    private int reservedTaskCount;
    private int maxConcurrentTasks = 1;
    private int healthScore = 100;
    private Map<String, Object> capabilityProfile = new LinkedHashMap<>();
    private Map<String, Object> runtimeLoad = new LinkedHashMap<>();
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

    public boolean isAssignable() {
        if (isRuntimeBackoffActive()) {
            return false;
        }
        if (draining || status == null || status == AgentStatus.DRAINING
                || status == AgentStatus.OFFLINE || status == AgentStatus.EXPIRED || status == AgentStatus.ERROR) {
            return false;
        }
        // P6.5: treat capacity facts as the dispatch authority for non-terminal
        // workload states.  Runtime snapshots can briefly persist BUSY while the
        // same row already reports available slots/current=0/reserved=0.  In that
        // case the operator view shows the Agent as IDLE/ELIGIBLE, but routing
        // capped the score at 49 because status BUSY made isAssignable=false.
        // Do not let stale workload status override fresh capacity evidence.
        if (availableSlots > 0) {
            return true;
        }
        if (getEffectiveTaskCount() < Math.max(1, maxConcurrentTasks)) {
            return true;
        }
        return status.isAssignable();
    }

    public int getEffectiveTaskCount() {
        return Math.max(0, currentTaskCount) + Math.max(0, reservedTaskCount);
    }

    public boolean isRuntimeBackoffActive() {
        return runtimeBackoffUntil != null && runtimeBackoffUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public void setRegion(String region) { this.region = region; }
    public void setZone(String zone) { this.zone = zone; }
    public void setStatus(AgentStatus status) { this.status = status; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities); }
    public void setCurrentTaskCount(int currentTaskCount) { this.currentTaskCount = Math.max(0, currentTaskCount); }
    public void setReservedTaskCount(int reservedTaskCount) { this.reservedTaskCount = Math.max(0, reservedTaskCount); }
    public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = Math.max(1, maxConcurrentTasks); }
    public void setHealthScore(int healthScore) { this.healthScore = Math.max(0, Math.min(100, healthScore)); }
    public void setCapabilityProfile(Map<String, Object> capabilityProfile) { this.capabilityProfile = capabilityProfile == null ? new LinkedHashMap<>() : new LinkedHashMap<>(capabilityProfile); }
    public void setRuntimeLoad(Map<String, Object> runtimeLoad) { this.runtimeLoad = runtimeLoad == null ? new LinkedHashMap<>() : new LinkedHashMap<>(runtimeLoad); applyRuntimeLoad(this.runtimeLoad); }
    public void setPluginName(String pluginName) { this.pluginName = pluginName; }
    public void setPluginVersion(String pluginVersion) { this.pluginVersion = pluginVersion; }
    public void setCapabilityRevision(String capabilityRevision) { this.capabilityRevision = capabilityRevision; }
    public void setAvailableSlots(int availableSlots) { this.availableSlots = Math.max(0, availableSlots); }
    public void setCapacityUtilization(double capacityUtilization) { this.capacityUtilization = Math.max(0.0d, Math.min(1.0d, capacityUtilization)); }
    public void setOutboxPending(int outboxPending) { this.outboxPending = Math.max(0, outboxPending); }
    public void setOutboxInFlight(int outboxInFlight) { this.outboxInFlight = Math.max(0, outboxInFlight); }
    public void setRecoveryPendingAssignments(int recoveryPendingAssignments) { this.recoveryPendingAssignments = Math.max(0, recoveryPendingAssignments); }
    public void setDraining(boolean draining) { this.draining = draining; }
    public void setConnectedAt(OffsetDateTime connectedAt) { this.connectedAt = connectedAt; }
    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public void setDisconnectedAt(OffsetDateTime disconnectedAt) { this.disconnectedAt = disconnectedAt; }
    public void setLeaseExpiresAt(OffsetDateTime leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public void setRuntimeBackoffUntil(OffsetDateTime runtimeBackoffUntil) { this.runtimeBackoffUntil = runtimeBackoffUntil; }
    public void setRuntimeBackoffReason(String runtimeBackoffReason) { this.runtimeBackoffReason = runtimeBackoffReason; }
    public void setRuntimeFailureCount(int runtimeFailureCount) { this.runtimeFailureCount = Math.max(0, runtimeFailureCount); }

    private void applyRuntimeLoad(Map<String, Object> runtimeLoad) {
        if (runtimeLoad == null || runtimeLoad.isEmpty()) {
            return;
        }
        Integer activeTasks = intValue(runtimeLoad.get("activeTasks"));
        Integer maxConcurrent = intValue(runtimeLoad.get("maxConcurrentTasks"));
        Integer slots = intValue(runtimeLoad.get("availableSlots"));
        Double utilization = doubleValue(runtimeLoad.get("capacityUtilization"));
        Integer pending = intValue(runtimeLoad.get("outboxPending"));
        Integer inFlight = intValue(runtimeLoad.get("outboxInFlight"));
        Integer recovery = intValue(runtimeLoad.get("recoveryPendingAssignments"));
        Boolean drainingValue = booleanValue(runtimeLoad.get("draining"));
        if (activeTasks != null) setCurrentTaskCount(activeTasks);
        if (maxConcurrent != null) setMaxConcurrentTasks(maxConcurrent);
        if (slots != null) setAvailableSlots(slots);
        if (utilization != null) setCapacityUtilization(utilization);
        if (pending != null) setOutboxPending(pending);
        if (inFlight != null) setOutboxInFlight(inFlight);
        if (recovery != null) setRecoveryPendingAssignments(recovery);
        if (drainingValue != null) setDraining(drainingValue);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Double.parseDouble(text.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text && !text.isBlank()) return Boolean.parseBoolean(text.trim());
        return null;
    }
}
