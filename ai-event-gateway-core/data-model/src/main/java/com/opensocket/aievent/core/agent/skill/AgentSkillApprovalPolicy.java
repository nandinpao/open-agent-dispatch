package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Role policy for Skill Registry workflow transitions. */
public class AgentSkillApprovalPolicy {
    private String skillCode;
    private boolean enabled = true;
    private List<String> submitRoles = new ArrayList<>(List.of("SYSADMIN", "ADMIN", "COMPLIANCE"));
    private List<String> approveRoles = new ArrayList<>(List.of("SYSADMIN", "ADMIN", "COMPLIANCE"));
    private List<String> publishRoles = new ArrayList<>(List.of("SYSADMIN", "ADMIN"));
    private List<String> rollbackRoles = new ArrayList<>(List.of("SYSADMIN", "ADMIN"));
    private boolean separationOfDuties = true;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getSubmitRoles() { return submitRoles; }
    public void setSubmitRoles(List<String> submitRoles) { this.submitRoles = submitRoles == null ? new ArrayList<>() : new ArrayList<>(submitRoles); }
    public List<String> getApproveRoles() { return approveRoles; }
    public void setApproveRoles(List<String> approveRoles) { this.approveRoles = approveRoles == null ? new ArrayList<>() : new ArrayList<>(approveRoles); }
    public List<String> getPublishRoles() { return publishRoles; }
    public void setPublishRoles(List<String> publishRoles) { this.publishRoles = publishRoles == null ? new ArrayList<>() : new ArrayList<>(publishRoles); }
    public List<String> getRollbackRoles() { return rollbackRoles; }
    public void setRollbackRoles(List<String> rollbackRoles) { this.rollbackRoles = rollbackRoles == null ? new ArrayList<>() : new ArrayList<>(rollbackRoles); }
    public boolean isSeparationOfDuties() { return separationOfDuties; }
    public void setSeparationOfDuties(boolean separationOfDuties) { this.separationOfDuties = separationOfDuties; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
}
