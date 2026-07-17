package com.opensocket.aievent.core.agent.skill;

import java.util.ArrayList;
import java.util.List;

public class AgentApprovedSkillSyncCommand {
    private List<String> skillCodes = new ArrayList<>();
    private Boolean enabled;
    private Boolean syncProfileCapabilities;
    private String operatorId;
    private String reason;

    public List<String> getSkillCodes() { return skillCodes; }
    public void setSkillCodes(List<String> skillCodes) { this.skillCodes = skillCodes == null ? new ArrayList<>() : new ArrayList<>(skillCodes); }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getSyncProfileCapabilities() { return syncProfileCapabilities; }
    public void setSyncProfileCapabilities(Boolean syncProfileCapabilities) { this.syncProfileCapabilities = syncProfileCapabilities; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
