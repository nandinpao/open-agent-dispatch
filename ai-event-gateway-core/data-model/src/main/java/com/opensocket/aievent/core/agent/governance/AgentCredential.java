package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;

public class AgentCredential {
    private String credentialId;
    private String agentId;
    private AgentCredentialType credentialType = AgentCredentialType.TOKEN;
    private String publicKeyFingerprint;
    private String tokenHash;
    private int credentialVersion = 1;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private String revokedReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public boolean activeAt(OffsetDateTime now) {
        return revokedAt == null && (expiresAt == null || !expiresAt.isBefore(now));
    }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public AgentCredentialType getCredentialType() { return credentialType; }
    public void setCredentialType(AgentCredentialType credentialType) { this.credentialType = credentialType == null ? AgentCredentialType.TOKEN : credentialType; }
    public String getPublicKeyFingerprint() { return publicKeyFingerprint; }
    public void setPublicKeyFingerprint(String publicKeyFingerprint) { this.publicKeyFingerprint = publicKeyFingerprint; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public int getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(int credentialVersion) { this.credentialVersion = Math.max(1, credentialVersion); }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
