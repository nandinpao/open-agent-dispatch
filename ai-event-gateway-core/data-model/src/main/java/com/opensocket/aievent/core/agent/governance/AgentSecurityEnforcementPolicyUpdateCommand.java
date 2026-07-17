package com.opensocket.aievent.core.agent.governance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentSecurityEnforcementPolicyUpdateCommand {
    private Boolean enabled;
    private AgentSecurityEnforcementMode duplicateRuntimeMode;
    private Boolean requireCredentialRotation;
    private Boolean notifyEmail;
    private Boolean notifySlack;
    private Boolean notifySiem;
    private List<String> emailRecipients;
    private List<String> slackChannels;
    private List<String> siemTopics;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String operatorId;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public AgentSecurityEnforcementMode getDuplicateRuntimeMode() { return duplicateRuntimeMode; }
    public void setDuplicateRuntimeMode(AgentSecurityEnforcementMode duplicateRuntimeMode) { this.duplicateRuntimeMode = duplicateRuntimeMode; }
    public Boolean getRequireCredentialRotation() { return requireCredentialRotation; }
    public void setRequireCredentialRotation(Boolean requireCredentialRotation) { this.requireCredentialRotation = requireCredentialRotation; }
    public Boolean getNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(Boolean notifyEmail) { this.notifyEmail = notifyEmail; }
    public Boolean getNotifySlack() { return notifySlack; }
    public void setNotifySlack(Boolean notifySlack) { this.notifySlack = notifySlack; }
    public Boolean getNotifySiem() { return notifySiem; }
    public void setNotifySiem(Boolean notifySiem) { this.notifySiem = notifySiem; }
    public List<String> getEmailRecipients() { return emailRecipients; }
    public void setEmailRecipients(List<String> emailRecipients) { this.emailRecipients = emailRecipients; }
    public List<String> getSlackChannels() { return slackChannels; }
    public void setSlackChannels(List<String> slackChannels) { this.slackChannels = slackChannels; }
    public List<String> getSiemTopics() { return siemTopics; }
    public void setSiemTopics(List<String> siemTopics) { this.siemTopics = siemTopics; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
}
