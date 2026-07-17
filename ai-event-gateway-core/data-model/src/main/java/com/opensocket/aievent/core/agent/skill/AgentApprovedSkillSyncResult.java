package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgentApprovedSkillSyncResult {
    private String agentId;
    private List<String> approvedSkillCodes = new ArrayList<>();
    private List<String> profileCapabilityCodes = new ArrayList<>();
    private List<String> addedToApprovedSkills = new ArrayList<>();
    private List<String> addedToProfileCapabilities = new ArrayList<>();
    private boolean profileCapabilitiesSynced;
    private String reason;
    private OffsetDateTime syncedAt;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public List<String> getApprovedSkillCodes() { return approvedSkillCodes; }
    public void setApprovedSkillCodes(List<String> approvedSkillCodes) { this.approvedSkillCodes = approvedSkillCodes == null ? new ArrayList<>() : new ArrayList<>(approvedSkillCodes); }
    public List<String> getProfileCapabilityCodes() { return profileCapabilityCodes; }
    public void setProfileCapabilityCodes(List<String> profileCapabilityCodes) { this.profileCapabilityCodes = profileCapabilityCodes == null ? new ArrayList<>() : new ArrayList<>(profileCapabilityCodes); }
    public List<String> getAddedToApprovedSkills() { return addedToApprovedSkills; }
    public void setAddedToApprovedSkills(List<String> addedToApprovedSkills) { this.addedToApprovedSkills = addedToApprovedSkills == null ? new ArrayList<>() : new ArrayList<>(addedToApprovedSkills); }
    public List<String> getAddedToProfileCapabilities() { return addedToProfileCapabilities; }
    public void setAddedToProfileCapabilities(List<String> addedToProfileCapabilities) { this.addedToProfileCapabilities = addedToProfileCapabilities == null ? new ArrayList<>() : new ArrayList<>(addedToProfileCapabilities); }
    public boolean isProfileCapabilitiesSynced() { return profileCapabilitiesSynced; }
    public void setProfileCapabilitiesSynced(boolean profileCapabilitiesSynced) { this.profileCapabilitiesSynced = profileCapabilitiesSynced; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(OffsetDateTime syncedAt) { this.syncedAt = syncedAt; }
}
