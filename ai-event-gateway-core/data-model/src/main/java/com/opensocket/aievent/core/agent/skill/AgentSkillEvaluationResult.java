package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgentSkillEvaluationResult {
    private String agentId;
    private String taxonomyVersion;
    private boolean eligible;
    private String reason;
    private List<String> missingRequirements = new ArrayList<>();
    private List<String> matchedSkillCodes = new ArrayList<>();
    private List<String> approvedSkillCodes = new ArrayList<>();
    private List<String> reportedSkillCodes = new ArrayList<>();
    private List<String> effectiveSkillCodes = new ArrayList<>();
    private OffsetDateTime evaluatedAt;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getTaxonomyVersion() { return taxonomyVersion; }
    public void setTaxonomyVersion(String taxonomyVersion) { this.taxonomyVersion = taxonomyVersion; }
    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getMissingRequirements() { return missingRequirements; }
    public void setMissingRequirements(List<String> missingRequirements) { this.missingRequirements = missingRequirements == null ? new ArrayList<>() : new ArrayList<>(missingRequirements); }
    public List<String> getMatchedSkillCodes() { return matchedSkillCodes; }
    public void setMatchedSkillCodes(List<String> matchedSkillCodes) { this.matchedSkillCodes = matchedSkillCodes == null ? new ArrayList<>() : new ArrayList<>(matchedSkillCodes); }
    public List<String> getApprovedSkillCodes() { return approvedSkillCodes; }
    public void setApprovedSkillCodes(List<String> approvedSkillCodes) { this.approvedSkillCodes = approvedSkillCodes == null ? new ArrayList<>() : new ArrayList<>(approvedSkillCodes); }
    public List<String> getReportedSkillCodes() { return reportedSkillCodes; }
    public void setReportedSkillCodes(List<String> reportedSkillCodes) { this.reportedSkillCodes = reportedSkillCodes == null ? new ArrayList<>() : new ArrayList<>(reportedSkillCodes); }
    public List<String> getEffectiveSkillCodes() { return effectiveSkillCodes; }
    public void setEffectiveSkillCodes(List<String> effectiveSkillCodes) { this.effectiveSkillCodes = effectiveSkillCodes == null ? new ArrayList<>() : new ArrayList<>(effectiveSkillCodes); }
    public OffsetDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(OffsetDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
