package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.List;

public class AgentEnrollmentApprovalCommand {
    private String agentId;
    private String approvedBy;
    private String tenantId;
    private String agentName;
    private String agentType;
    private String ownerTeam;
    private String description;
    private String comment;
    private List<String> capabilities = List.of();
    private List<AgentAuthorizationScope> scopes = List.of();
    private AgentCredentialType credentialType = AgentCredentialType.TOKEN;
    private String credentialToken;
    private String credentialHash;
    private String publicKeyFingerprint;
    private OffsetDateTime credentialExpiresAt;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
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
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities); }
    public List<AgentAuthorizationScope> getScopes() { return scopes; }
    public void setScopes(List<AgentAuthorizationScope> scopes) { this.scopes = scopes == null ? List.of() : List.copyOf(scopes); }
    public AgentCredentialType getCredentialType() { return credentialType; }
    public void setCredentialType(AgentCredentialType credentialType) { this.credentialType = credentialType == null ? AgentCredentialType.TOKEN : credentialType; }
    public String getCredentialToken() { return credentialToken; }
    public void setCredentialToken(String credentialToken) { this.credentialToken = credentialToken; }
    public String getCredentialHash() { return credentialHash; }
    public void setCredentialHash(String credentialHash) { this.credentialHash = credentialHash; }
    public String getPublicKeyFingerprint() { return publicKeyFingerprint; }
    public void setPublicKeyFingerprint(String publicKeyFingerprint) { this.publicKeyFingerprint = publicKeyFingerprint; }
    public OffsetDateTime getCredentialExpiresAt() { return credentialExpiresAt; }
    public void setCredentialExpiresAt(OffsetDateTime credentialExpiresAt) { this.credentialExpiresAt = credentialExpiresAt; }
}
