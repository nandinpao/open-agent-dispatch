package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgentProfile {
    private String agentId;
    private String tenantId;
    private String agentName;
    private String agentType;
    private String ownerTeam;
    private String description;
    private AgentApprovalStatus approvalStatus = AgentApprovalStatus.PENDING_REVIEW;
    private boolean enabled;
    private AgentRiskStatus riskStatus = AgentRiskStatus.NORMAL;
    private int policyVersion = 1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private AgentCredentialSummary credential;
    private List<AgentCapability> capabilities = new ArrayList<>();
    private List<AgentAuthorizationScope> authorizationScopes = new ArrayList<>();

    public boolean allowsConnection() {
        return approvalStatus == AgentApprovalStatus.APPROVED && enabled && riskStatus == AgentRiskStatus.NORMAL;
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getOwnerTeam() { return ownerTeam; }
    public void setOwnerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AgentApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(AgentApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus == null ? AgentApprovalStatus.PENDING_REVIEW : approvalStatus; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public AgentRiskStatus getRiskStatus() { return riskStatus; }
    public void setRiskStatus(AgentRiskStatus riskStatus) { this.riskStatus = riskStatus == null ? AgentRiskStatus.NORMAL : riskStatus; }
    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = Math.max(1, policyVersion); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public AgentCredentialSummary getCredential() { return credential; }
    public void setCredential(AgentCredentialSummary credential) { this.credential = credential; }
    public List<AgentCapability> getCapabilities() { return capabilities; }
    public void setCapabilities(List<AgentCapability> capabilities) { this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities); }
    public List<AgentAuthorizationScope> getAuthorizationScopes() { return authorizationScopes; }
    public void setAuthorizationScopes(List<AgentAuthorizationScope> authorizationScopes) { this.authorizationScopes = authorizationScopes == null ? new ArrayList<>() : new ArrayList<>(authorizationScopes); }
}
