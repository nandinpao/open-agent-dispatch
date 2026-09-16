package com.opensocket.aievent.core.agent.governance;

import java.util.List;

public class AgentProfileUpdateCommand {
    private String tenantId;
    private String agentName;
    private String agentType;
    private String ownerTeam;
    private String ownerDepartmentId;
    private String ownerGroupId;
    private String businessOwnerUserId;
    private String technicalStewardUserId;
    private String responsibilityRoleId;
    private String serviceDomainId;
    private String description;
    private AgentApprovalStatus approvalStatus;
    private Boolean enabled;
    private AgentRiskStatus riskStatus;
    private List<String> capabilities;
    private List<AgentAuthorizationScope> scopes;
    private String operatorId;
    private String reason;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getOwnerTeam() { return ownerTeam; }
    public void setOwnerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; }
    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String ownerDepartmentId) { this.ownerDepartmentId = ownerDepartmentId; }
    public String getOwnerGroupId() { return ownerGroupId; }
    public void setOwnerGroupId(String ownerGroupId) { this.ownerGroupId = ownerGroupId; }
    public String getBusinessOwnerUserId() { return businessOwnerUserId; }
    public void setBusinessOwnerUserId(String value) { this.businessOwnerUserId = value; }
    public String getTechnicalStewardUserId() { return technicalStewardUserId; }
    public void setTechnicalStewardUserId(String value) { this.technicalStewardUserId = value; }
    public String getResponsibilityRoleId() { return responsibilityRoleId; }
    public void setResponsibilityRoleId(String value) { this.responsibilityRoleId = value; }
    public String getServiceDomainId() { return serviceDomainId; }
    public void setServiceDomainId(String value) { this.serviceDomainId = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AgentApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(AgentApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public AgentRiskStatus getRiskStatus() { return riskStatus; }
    public void setRiskStatus(AgentRiskStatus riskStatus) { this.riskStatus = riskStatus; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
    public List<AgentAuthorizationScope> getScopes() { return scopes; }
    public void setScopes(List<AgentAuthorizationScope> scopes) { this.scopes = scopes; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
