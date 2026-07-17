package com.opensocket.aievent.core.dispatch.flow;

/** Runtime routing member of an Agent Pool / Work Queue. */
public class AgentPoolRoutingMember {
    private String tenantId;
    private String poolId;
    private String agentId;
    private String memberStatus;
    private int priority = 100;
    private int weight = 1;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPoolId() { return poolId; }
    public void setPoolId(String poolId) { this.poolId = poolId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getMemberStatus() { return memberStatus; }
    public void setMemberStatus(String memberStatus) { this.memberStatus = memberStatus; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
}
