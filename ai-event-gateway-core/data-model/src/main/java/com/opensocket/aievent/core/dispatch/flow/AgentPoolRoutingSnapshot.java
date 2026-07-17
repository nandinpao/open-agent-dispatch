package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;

/** Active Agent Pool / Work Queue snapshot used by routing. */
public class AgentPoolRoutingSnapshot {
    private String tenantId;
    private String poolId;
    private String poolCode;
    private String poolName;
    private String sourceSystem;
    private String poolType;
    private String selectionStrategy = "LOWEST_LOAD";
    private String status = "ACTIVE";
    private List<AgentPoolRoutingMember> members = List.of();

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
    public List<AgentPoolRoutingMember> getMembers() { return members; }
    public void setMembers(List<AgentPoolRoutingMember> members) { this.members = members == null ? List.of() : List.copyOf(members); }
}
