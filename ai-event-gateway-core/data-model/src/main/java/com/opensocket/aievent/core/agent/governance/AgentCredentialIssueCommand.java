package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;

/**
 * Admin command for issuing or rotating credential material for an existing Core Agent profile.
 *
 * <p>This is intentionally separate from enrollment approval because existing APPROVED profiles
 * may have been created before credential enforcement was introduced, or may need manual
 * credential rotation after a review correction.</p>
 */
public class AgentCredentialIssueCommand {
    private String operatorId;
    private String reason;
    private AgentCredentialType credentialType = AgentCredentialType.TOKEN;
    private String credentialToken;
    private String credentialHash;
    private String publicKeyFingerprint;
    private OffsetDateTime credentialExpiresAt;
    private boolean revokeExisting = true;

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
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
    public boolean isRevokeExisting() { return revokeExisting; }
    public void setRevokeExisting(boolean revokeExisting) { this.revokeExisting = revokeExisting; }
}
