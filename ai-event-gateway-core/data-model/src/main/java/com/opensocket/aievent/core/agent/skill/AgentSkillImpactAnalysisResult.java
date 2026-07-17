package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Impact analysis computed before approving/publishing a Skill Registry version. */
public class AgentSkillImpactAnalysisResult {
    private String skillCode;
    private int version;
    private String severity = "LOW";
    private boolean breakingChange;
    private List<AgentSkillImpactAgent> impactedAgents = new ArrayList<>();
    private List<String> impactedAgentIds = new ArrayList<>();
    private List<String> impactedTaskTypes = new ArrayList<>();
    private List<String> impactedProviders = new ArrayList<>();
    private List<String> impactedDataClasses = new ArrayList<>();
    private List<String> impactedToolPolicies = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
    private AgentSkillDiffResult diff;
    private OffsetDateTime generatedAt;

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = Math.max(0, version); }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public boolean isBreakingChange() { return breakingChange; }
    public void setBreakingChange(boolean breakingChange) { this.breakingChange = breakingChange; }
    public List<AgentSkillImpactAgent> getImpactedAgents() { return impactedAgents; }
    public void setImpactedAgents(List<AgentSkillImpactAgent> impactedAgents) { this.impactedAgents = impactedAgents == null ? new ArrayList<>() : new ArrayList<>(impactedAgents); }
    public List<String> getImpactedAgentIds() { return impactedAgentIds; }
    public void setImpactedAgentIds(List<String> impactedAgentIds) { this.impactedAgentIds = impactedAgentIds == null ? new ArrayList<>() : new ArrayList<>(impactedAgentIds); }
    public List<String> getImpactedTaskTypes() { return impactedTaskTypes; }
    public void setImpactedTaskTypes(List<String> impactedTaskTypes) { this.impactedTaskTypes = impactedTaskTypes == null ? new ArrayList<>() : new ArrayList<>(impactedTaskTypes); }
    public List<String> getImpactedProviders() { return impactedProviders; }
    public void setImpactedProviders(List<String> impactedProviders) { this.impactedProviders = impactedProviders == null ? new ArrayList<>() : new ArrayList<>(impactedProviders); }
    public List<String> getImpactedDataClasses() { return impactedDataClasses; }
    public void setImpactedDataClasses(List<String> impactedDataClasses) { this.impactedDataClasses = impactedDataClasses == null ? new ArrayList<>() : new ArrayList<>(impactedDataClasses); }
    public List<String> getImpactedToolPolicies() { return impactedToolPolicies; }
    public void setImpactedToolPolicies(List<String> impactedToolPolicies) { this.impactedToolPolicies = impactedToolPolicies == null ? new ArrayList<>() : new ArrayList<>(impactedToolPolicies); }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes == null ? new ArrayList<>() : new ArrayList<>(notes); }
    public AgentSkillDiffResult getDiff() { return diff; }
    public void setDiff(AgentSkillDiffResult diff) { this.diff = diff; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
