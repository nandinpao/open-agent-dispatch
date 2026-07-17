package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;

/** Agent grant impacted by a Skill Registry definition change. */
public class AgentSkillImpactAgent {
    private String agentId;
    private String skillCode;
    private int policyVersion;
    private boolean enabled;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String impactReason;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = Math.max(1, policyVersion); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getImpactReason() { return impactReason; }
    public void setImpactReason(String impactReason) { this.impactReason = impactReason; }
}
