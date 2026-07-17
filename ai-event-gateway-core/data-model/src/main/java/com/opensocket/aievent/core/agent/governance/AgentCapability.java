package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;

public class AgentCapability {
    private String agentId;
    private String capabilityCode;
    private String capabilityVersion;
    private boolean enabled = true;
    private String approvedBy;
    private OffsetDateTime approvedAt;

    public AgentCapability() {}
    public AgentCapability(String agentId, String capabilityCode) {
        this.agentId = agentId;
        this.capabilityCode = capabilityCode;
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getCapabilityCode() { return capabilityCode; }
    public void setCapabilityCode(String capabilityCode) { this.capabilityCode = capabilityCode; }
    public String getCapabilityVersion() { return capabilityVersion; }
    public void setCapabilityVersion(String capabilityVersion) { this.capabilityVersion = capabilityVersion; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
}
