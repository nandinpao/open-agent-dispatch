package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;

/**
 * Admin command for restoring an existing Core Agent profile back to APPROVED.
 *
 * <p>This command is used for manual governance corrections such as
 * REJECTED -> APPROVED, SUSPENDED -> APPROVED, or REVOKED -> APPROVED.
 * Credential material is optional only when the profile already has an active
 * credential and is not security-blocked. REVOKED, SUSPENDED, QUARANTINED, or
 * COMPROMISED profiles must be restored with new credential material so an
 * enrollment correction cannot silently re-enable a blocked Agent identity.</p>
 */
public class AgentProfileApprovalCommand {
    private String operatorId;
    private String reason;
    private Boolean enabled = Boolean.TRUE;
    private AgentRiskStatus riskStatus = AgentRiskStatus.NORMAL;
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
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public AgentRiskStatus getRiskStatus() { return riskStatus; }
    public void setRiskStatus(AgentRiskStatus riskStatus) { this.riskStatus = riskStatus == null ? AgentRiskStatus.NORMAL : riskStatus; }
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
