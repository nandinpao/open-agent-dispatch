package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;

/**
 * Browser/API safe credential view. Does not expose token_hash or other secret material.
 */
public class AgentCredentialSummary {
    private String credentialId;
    private AgentCredentialType credentialType;
    private int credentialVersion;
    private AgentCredentialStatus credentialStatus = AgentCredentialStatus.ACTIVE;
    private String publicKeyFingerprint;
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public AgentCredentialType getCredentialType() { return credentialType; }
    public void setCredentialType(AgentCredentialType credentialType) { this.credentialType = credentialType; }
    public int getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(int credentialVersion) { this.credentialVersion = credentialVersion; }
    public AgentCredentialStatus getCredentialStatus() { return credentialStatus; }
    public void setCredentialStatus(AgentCredentialStatus credentialStatus) { this.credentialStatus = credentialStatus == null ? AgentCredentialStatus.ACTIVE : credentialStatus; }
    public String getPublicKeyFingerprint() { return publicKeyFingerprint; }
    public void setPublicKeyFingerprint(String publicKeyFingerprint) { this.publicKeyFingerprint = publicKeyFingerprint; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
}
