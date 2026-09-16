package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.List;

import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;

public class AgentConnectionAuthorizationResult {
    private AgentAuthorizationDecision decision;
    private AgentAuthorizationDenyReason reason = AgentAuthorizationDenyReason.NONE;
    private String message;
    private String agentId;
    private String tenantId;
    private String principalType = "AGENT";
    private String principalId;
    private String ownerDepartmentId;
    private String ownerGroupId;
    private AgentApprovalStatus approvalStatus;
    private boolean enabled;
    private AgentRiskStatus riskStatus;
    private List<String> capabilities = List.of();
    private List<String> allowedTaskTypes = List.of();
    private List<String> allowedSystemCodes = List.of();
    private String credentialId;
    private AgentCredentialType credentialType;
    private OffsetDateTime credentialIssuedAt;
    private OffsetDateTime credentialExpiresAt;
    private int credentialVersion;
    private int policyVersion;
    private MachineAuthenticationContext machineAuthentication;
    private boolean securityEventRequired;

    public static AgentConnectionAuthorizationResult allow(AgentProfile profile, List<String> capabilities,
                                                           List<String> taskTypes, List<String> systemCodes,
                                                           int credentialVersion) {
        return allow(profile, capabilities, taskTypes, systemCodes, null, credentialVersion);
    }

    public static AgentConnectionAuthorizationResult allow(AgentProfile profile, List<String> capabilities,
                                                           List<String> taskTypes, List<String> systemCodes,
                                                           AgentCredential credential, int credentialVersion) {
        AgentConnectionAuthorizationResult result = new AgentConnectionAuthorizationResult();
        result.setDecision(AgentAuthorizationDecision.ALLOW);
        result.setAgentId(profile.getAgentId());
        result.setTenantId(profile.getTenantId());
        result.setPrincipalId(profile.getAgentId());
        result.setOwnerDepartmentId(profile.getOwnerDepartmentId());
        result.setOwnerGroupId(profile.getOwnerGroupId());
        result.setApprovalStatus(profile.getApprovalStatus());
        result.setEnabled(profile.isEnabled());
        result.setRiskStatus(profile.getRiskStatus());
        result.setCapabilities(capabilities);
        result.setAllowedTaskTypes(taskTypes);
        result.setAllowedSystemCodes(systemCodes);
        if (credential != null) {
            result.setCredentialId(credential.getCredentialId());
            result.setCredentialType(credential.getCredentialType());
            result.setCredentialIssuedAt(credential.getIssuedAt());
            result.setCredentialExpiresAt(credential.getExpiresAt());
        }
        result.setCredentialVersion(credentialVersion);
        result.setPolicyVersion(profile.getPolicyVersion());
        result.setMessage("Agent connection authorized by Core governance");
        return result;
    }

    public static AgentConnectionAuthorizationResult deny(String agentId, AgentAuthorizationDenyReason reason, String message) {
        AgentConnectionAuthorizationResult result = new AgentConnectionAuthorizationResult();
        result.setDecision(AgentAuthorizationDecision.DENY);
        result.setReason(reason == null ? AgentAuthorizationDenyReason.INTERNAL_ERROR : reason);
        result.setAgentId(agentId);
        result.setMessage(message);
        result.setSecurityEventRequired(true);
        return result;
    }

    public static AgentConnectionAuthorizationResult deny(AgentProfile profile, AgentAuthorizationDenyReason reason, String message) {
        AgentConnectionAuthorizationResult result = deny(profile == null ? null : profile.getAgentId(), reason, message);
        if (profile != null) {
            result.setTenantId(profile.getTenantId());
            result.setPrincipalId(profile.getAgentId());
            result.setOwnerDepartmentId(profile.getOwnerDepartmentId());
            result.setOwnerGroupId(profile.getOwnerGroupId());
            result.setApprovalStatus(profile.getApprovalStatus());
            result.setEnabled(profile.isEnabled());
            result.setRiskStatus(profile.getRiskStatus());
            result.setPolicyVersion(profile.getPolicyVersion());
        }
        return result;
    }

    public AgentAuthorizationDecision getDecision() { return decision; }
    public void setDecision(AgentAuthorizationDecision decision) { this.decision = decision; }
    public AgentAuthorizationDenyReason getReason() { return reason; }
    public void setReason(AgentAuthorizationDenyReason reason) { this.reason = reason == null ? AgentAuthorizationDenyReason.NONE : reason; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPrincipalType() { return principalType; }
    public void setPrincipalType(String principalType) { this.principalType = principalType == null || principalType.isBlank() ? "AGENT" : principalType.trim(); }
    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String ownerDepartmentId) { this.ownerDepartmentId = ownerDepartmentId; }
    public String getOwnerGroupId() { return ownerGroupId; }
    public void setOwnerGroupId(String ownerGroupId) { this.ownerGroupId = ownerGroupId; }
    public AgentApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(AgentApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public AgentRiskStatus getRiskStatus() { return riskStatus; }
    public void setRiskStatus(AgentRiskStatus riskStatus) { this.riskStatus = riskStatus; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities); }
    public List<String> getAllowedTaskTypes() { return allowedTaskTypes; }
    public void setAllowedTaskTypes(List<String> allowedTaskTypes) { this.allowedTaskTypes = allowedTaskTypes == null ? List.of() : List.copyOf(allowedTaskTypes); }
    public List<String> getAllowedSystemCodes() { return allowedSystemCodes; }
    public void setAllowedSystemCodes(List<String> allowedSystemCodes) { this.allowedSystemCodes = allowedSystemCodes == null ? List.of() : List.copyOf(allowedSystemCodes); }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public AgentCredentialType getCredentialType() { return credentialType; }
    public void setCredentialType(AgentCredentialType credentialType) { this.credentialType = credentialType; }
    public OffsetDateTime getCredentialIssuedAt() { return credentialIssuedAt; }
    public void setCredentialIssuedAt(OffsetDateTime credentialIssuedAt) { this.credentialIssuedAt = credentialIssuedAt; }
    public OffsetDateTime getCredentialExpiresAt() { return credentialExpiresAt; }
    public void setCredentialExpiresAt(OffsetDateTime credentialExpiresAt) { this.credentialExpiresAt = credentialExpiresAt; }
    public int getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(int credentialVersion) { this.credentialVersion = Math.max(0, credentialVersion); }
    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = Math.max(0, policyVersion); }
    public MachineAuthenticationContext getMachineAuthentication() { return machineAuthentication; }
    public void setMachineAuthentication(MachineAuthenticationContext machineAuthentication) { this.machineAuthentication = machineAuthentication; }
    public AgentServicePrincipalContext getServicePrincipal() {
        if (decision != AgentAuthorizationDecision.ALLOW || principalId == null || principalId.isBlank() || tenantId == null || tenantId.isBlank()) return null;
        return new AgentServicePrincipalContext(principalType, principalId, tenantId, ownerDepartmentId, ownerGroupId,
                capabilities, allowedTaskTypes, allowedSystemCodes, credentialVersion, policyVersion);
    }
    public boolean isSecurityEventRequired() { return securityEventRequired; }
    public void setSecurityEventRequired(boolean securityEventRequired) { this.securityEventRequired = securityEventRequired; }
}
