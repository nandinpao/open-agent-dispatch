package com.opensocket.aievent.core.agent.governance;

import java.util.List;

public class AgentConnectionAuthorizationResult {
    private AgentAuthorizationDecision decision;
    private AgentAuthorizationDenyReason reason = AgentAuthorizationDenyReason.NONE;
    private String message;
    private String agentId;
    private String tenantId;
    private AgentApprovalStatus approvalStatus;
    private boolean enabled;
    private AgentRiskStatus riskStatus;
    private List<String> capabilities = List.of();
    private List<String> allowedTaskTypes = List.of();
    private List<String> allowedSystemCodes = List.of();
    private int credentialVersion;
    private int policyVersion;
    private boolean securityEventRequired;

    public static AgentConnectionAuthorizationResult allow(AgentProfile profile, List<String> capabilities,
                                                           List<String> taskTypes, List<String> systemCodes,
                                                           int credentialVersion) {
        AgentConnectionAuthorizationResult result = new AgentConnectionAuthorizationResult();
        result.setDecision(AgentAuthorizationDecision.ALLOW);
        result.setAgentId(profile.getAgentId());
        result.setTenantId(profile.getTenantId());
        result.setApprovalStatus(profile.getApprovalStatus());
        result.setEnabled(profile.isEnabled());
        result.setRiskStatus(profile.getRiskStatus());
        result.setCapabilities(capabilities);
        result.setAllowedTaskTypes(taskTypes);
        result.setAllowedSystemCodes(systemCodes);
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
    public int getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(int credentialVersion) { this.credentialVersion = Math.max(0, credentialVersion); }
    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = Math.max(0, policyVersion); }
    public boolean isSecurityEventRequired() { return securityEventRequired; }
    public void setSecurityEventRequired(boolean securityEventRequired) { this.securityEventRequired = securityEventRequired; }
}
