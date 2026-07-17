package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Policy owned by Core that determines how a duplicate runtime security observation should be handled.
 * agentId="*" is the default policy used when an Agent does not have a dedicated override.
 */
public class AgentSecurityEnforcementPolicy {
    private String policyId = UUID.randomUUID().toString();
    private String agentId = "*";
    private boolean enabled = true;
    private AgentSecurityEnforcementMode duplicateRuntimeMode = AgentSecurityEnforcementMode.ALERT_ONLY;
    private boolean requireCredentialRotation = true;
    private boolean notifyEmail;
    private boolean notifySlack;
    private boolean notifySiem;
    private List<String> emailRecipients = List.of();
    private List<String> slackChannels = List.of();
    private List<String> siemTopics = List.of();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public boolean shouldQuarantine() {
        return duplicateRuntimeMode == AgentSecurityEnforcementMode.QUARANTINE
                || duplicateRuntimeMode == AgentSecurityEnforcementMode.QUARANTINE_AND_DISCONNECT
                || duplicateRuntimeMode == AgentSecurityEnforcementMode.QUARANTINE_REVOKE_AND_DISCONNECT;
    }

    public boolean shouldDisconnectAll() {
        return duplicateRuntimeMode == AgentSecurityEnforcementMode.QUARANTINE_AND_DISCONNECT
                || duplicateRuntimeMode == AgentSecurityEnforcementMode.QUARANTINE_REVOKE_AND_DISCONNECT;
    }

    public boolean shouldRevokeCredentials() {
        return duplicateRuntimeMode == AgentSecurityEnforcementMode.QUARANTINE_REVOKE_AND_DISCONNECT;
    }

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public AgentSecurityEnforcementMode getDuplicateRuntimeMode() { return duplicateRuntimeMode; }
    public void setDuplicateRuntimeMode(AgentSecurityEnforcementMode duplicateRuntimeMode) { this.duplicateRuntimeMode = duplicateRuntimeMode; }
    public boolean isRequireCredentialRotation() { return requireCredentialRotation; }
    public void setRequireCredentialRotation(boolean requireCredentialRotation) { this.requireCredentialRotation = requireCredentialRotation; }
    public boolean isNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(boolean notifyEmail) { this.notifyEmail = notifyEmail; }
    public boolean isNotifySlack() { return notifySlack; }
    public void setNotifySlack(boolean notifySlack) { this.notifySlack = notifySlack; }
    public boolean isNotifySiem() { return notifySiem; }
    public void setNotifySiem(boolean notifySiem) { this.notifySiem = notifySiem; }
    public List<String> getEmailRecipients() { return emailRecipients; }
    public void setEmailRecipients(List<String> emailRecipients) { this.emailRecipients = emailRecipients == null ? List.of() : List.copyOf(emailRecipients); }
    public List<String> getSlackChannels() { return slackChannels; }
    public void setSlackChannels(List<String> slackChannels) { this.slackChannels = slackChannels == null ? List.of() : List.copyOf(slackChannels); }
    public List<String> getSiemTopics() { return siemTopics; }
    public void setSiemTopics(List<String> siemTopics) { this.siemTopics = siemTopics == null ? List.of() : List.copyOf(siemTopics); }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
