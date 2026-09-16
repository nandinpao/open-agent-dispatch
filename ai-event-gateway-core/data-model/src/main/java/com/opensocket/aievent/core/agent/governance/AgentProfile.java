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
    private String ownerDepartmentId = "UNASSIGNED";
    private String ownerGroupId;
    private String businessOwnerUserId;
    private String technicalStewardUserId;
    private String responsibilityRoleId;
    private String responsibilityBindingId;
    private String ownershipReviewStatus = "REVIEW_REQUIRED";
    private String ownershipReviewReason;
    private OffsetDateTime nextOwnershipReviewAt;
    private String serviceDomainId = "UNASSIGNED";
    private String trustZoneId = "DEFAULT";
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
        boolean reviewCurrent = "CURRENT".equalsIgnoreCase(ownershipReviewStatus)
                && (nextOwnershipReviewAt == null || nextOwnershipReviewAt.isAfter(OffsetDateTime.now()));
        boolean organizationOwned = ownerDepartmentId != null && !ownerDepartmentId.isBlank() && !"UNASSIGNED".equalsIgnoreCase(ownerDepartmentId);
        boolean responsibilityBound = responsibilityRoleId != null && !responsibilityRoleId.isBlank();
        return approvalStatus == AgentApprovalStatus.APPROVED && enabled && riskStatus == AgentRiskStatus.NORMAL
                && organizationOwned && businessOwnerUserId != null && responsibilityBound && reviewCurrent;
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
    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String ownerDepartmentId) { this.ownerDepartmentId = defaultValue(ownerDepartmentId, "UNASSIGNED"); }
    public String getOwnerGroupId() { return ownerGroupId; }
    public void setOwnerGroupId(String ownerGroupId) { this.ownerGroupId = normalizeOptional(ownerGroupId); }
    public String getBusinessOwnerUserId() { return businessOwnerUserId; }
    public void setBusinessOwnerUserId(String value) { this.businessOwnerUserId = normalizeOptional(value); }
    public String getTechnicalStewardUserId() { return technicalStewardUserId; }
    public void setTechnicalStewardUserId(String value) { this.technicalStewardUserId = normalizeOptional(value); }
    public String getResponsibilityRoleId() { return responsibilityRoleId; }
    public void setResponsibilityRoleId(String value) { this.responsibilityRoleId = normalizeOptional(value); }
    public String getResponsibilityBindingId() { return responsibilityBindingId; }
    public void setResponsibilityBindingId(String value) { this.responsibilityBindingId = normalizeOptional(value); }
    public String getOwnershipReviewStatus() { return ownershipReviewStatus; }
    public void setOwnershipReviewStatus(String value) { this.ownershipReviewStatus = defaultValue(value, "REVIEW_REQUIRED"); }
    public String getOwnershipReviewReason() { return ownershipReviewReason; }
    public void setOwnershipReviewReason(String value) { this.ownershipReviewReason = normalizeOptional(value); }
    public OffsetDateTime getNextOwnershipReviewAt() { return nextOwnershipReviewAt; }
    public void setNextOwnershipReviewAt(OffsetDateTime value) { this.nextOwnershipReviewAt = value; }
    public String getServiceDomainId() { return serviceDomainId; }
    public void setServiceDomainId(String serviceDomainId) { this.serviceDomainId = defaultValue(serviceDomainId, "UNASSIGNED"); }
    public String getTrustZoneId() { return trustZoneId; }
    public void setTrustZoneId(String trustZoneId) { this.trustZoneId = defaultValue(trustZoneId, "DEFAULT"); }
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
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String normalizeOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public List<AgentAuthorizationScope> getAuthorizationScopes() { return authorizationScopes; }
    public void setAuthorizationScopes(List<AgentAuthorizationScope> authorizationScopes) { this.authorizationScopes = authorizationScopes == null ? new ArrayList<>() : new ArrayList<>(authorizationScopes); }
}
