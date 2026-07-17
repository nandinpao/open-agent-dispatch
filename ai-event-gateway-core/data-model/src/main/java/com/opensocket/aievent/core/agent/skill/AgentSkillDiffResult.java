package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Computed diff between an active/base skill definition and a candidate version. */
public class AgentSkillDiffResult {
    private String skillCode;
    private int baseVersion;
    private int targetVersion;
    private AgentSkillLifecycleStatus baseStatus;
    private AgentSkillLifecycleStatus targetStatus;
    private List<AgentSkillDiffEntry> entries = new ArrayList<>();
    private List<String> changedFields = new ArrayList<>();
    private List<String> breakingFields = new ArrayList<>();
    private boolean breakingChange;
    private String summary;
    private OffsetDateTime generatedAt;

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public int getBaseVersion() { return baseVersion; }
    public void setBaseVersion(int baseVersion) { this.baseVersion = Math.max(0, baseVersion); }
    public int getTargetVersion() { return targetVersion; }
    public void setTargetVersion(int targetVersion) { this.targetVersion = Math.max(0, targetVersion); }
    public AgentSkillLifecycleStatus getBaseStatus() { return baseStatus; }
    public void setBaseStatus(AgentSkillLifecycleStatus baseStatus) { this.baseStatus = baseStatus; }
    public AgentSkillLifecycleStatus getTargetStatus() { return targetStatus; }
    public void setTargetStatus(AgentSkillLifecycleStatus targetStatus) { this.targetStatus = targetStatus; }
    public List<AgentSkillDiffEntry> getEntries() { return entries; }
    public void setEntries(List<AgentSkillDiffEntry> entries) { this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries); }
    public List<String> getChangedFields() { return changedFields; }
    public void setChangedFields(List<String> changedFields) { this.changedFields = changedFields == null ? new ArrayList<>() : new ArrayList<>(changedFields); }
    public List<String> getBreakingFields() { return breakingFields; }
    public void setBreakingFields(List<String> breakingFields) { this.breakingFields = breakingFields == null ? new ArrayList<>() : new ArrayList<>(breakingFields); }
    public boolean isBreakingChange() { return breakingChange; }
    public void setBreakingChange(boolean breakingChange) { this.breakingChange = breakingChange; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
