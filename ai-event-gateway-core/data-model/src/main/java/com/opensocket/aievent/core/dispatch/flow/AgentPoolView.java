package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 32-B API model for Agent Pool / Work Queue persistence.
 *
 * <p>Pools are the first-version dispatch target. Capability remains Agent
 * metadata only and is not represented as a required routing gate here.</p>
 */
public class AgentPoolView {
    private String tenantId;
    private String poolId;
    private String poolCode;
    private String poolName;
    private String sourceSystem;
    private String poolType = "RESOLUTION";
    private String selectionStrategy = "LOWEST_LOAD";
    private String status = "ACTIVE";
    private String description;
    private Integer memberCount = 0;
    private Integer availableAgentCount = 0;
    private List<AgentPoolMemberView> members = List.of();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime updatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPoolId() { return poolId; }
    public void setPoolId(String poolId) { this.poolId = poolId; }
    public String getPoolCode() { return poolCode; }
    public void setPoolCode(String poolCode) { this.poolCode = poolCode; }
    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getPoolType() { return poolType; }
    public void setPoolType(String poolType) { this.poolType = poolType; }
    public String getSelectionStrategy() { return selectionStrategy; }
    public void setSelectionStrategy(String selectionStrategy) { this.selectionStrategy = selectionStrategy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getAvailableAgentCount() { return availableAgentCount; }
    public void setAvailableAgentCount(Integer availableAgentCount) { this.availableAgentCount = availableAgentCount; }
    public List<AgentPoolMemberView> getMembers() { return members; }
    public void setMembers(List<AgentPoolMemberView> members) { this.members = members == null ? List.of() : List.copyOf(members); }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
