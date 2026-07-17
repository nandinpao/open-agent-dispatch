package com.opensocket.aievent.core.agent.readiness;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationResult;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolveResult;

public class DispatchReadinessEvaluationResult {
    private boolean ready;
    private String summary;
    private String beginnerSummary;
    private String agentId;
    private List<String> requiredCapabilities = new ArrayList<>();
    private List<String> rawTaskRequirements = new ArrayList<>();
    private List<String> effectiveDispatchCapabilities = new ArrayList<>();
    private List<String> legacyTaskAliases = new ArrayList<>();
    private List<String> matchedSkillCodes = new ArrayList<>();
    private List<String> missingRequirements = new ArrayList<>();
    private List<DispatchReadinessCheck> checks = new ArrayList<>();
    private List<DispatchReadinessFixAction> recommendedActions = new ArrayList<>();
    private AgentSkillEvaluationResult skillEvaluation;
    private TaskDispatchContractResolveResult contractResolution;
    private Map<String, Object> labels = new LinkedHashMap<>();
    private OffsetDateTime evaluatedAt;

    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBeginnerSummary() { return beginnerSummary; }
    public void setBeginnerSummary(String beginnerSummary) { this.beginnerSummary = beginnerSummary; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities); }
    public List<String> getRawTaskRequirements() { return rawTaskRequirements; }
    public void setRawTaskRequirements(List<String> rawTaskRequirements) { this.rawTaskRequirements = rawTaskRequirements == null ? new ArrayList<>() : new ArrayList<>(rawTaskRequirements); }
    public List<String> getEffectiveDispatchCapabilities() { return effectiveDispatchCapabilities; }
    public void setEffectiveDispatchCapabilities(List<String> effectiveDispatchCapabilities) { this.effectiveDispatchCapabilities = effectiveDispatchCapabilities == null ? new ArrayList<>() : new ArrayList<>(effectiveDispatchCapabilities); }
    public List<String> getLegacyTaskAliases() { return legacyTaskAliases; }
    public void setLegacyTaskAliases(List<String> legacyTaskAliases) { this.legacyTaskAliases = legacyTaskAliases == null ? new ArrayList<>() : new ArrayList<>(legacyTaskAliases); }
    public List<String> getMatchedSkillCodes() { return matchedSkillCodes; }
    public void setMatchedSkillCodes(List<String> matchedSkillCodes) { this.matchedSkillCodes = matchedSkillCodes == null ? new ArrayList<>() : new ArrayList<>(matchedSkillCodes); }
    public List<String> getMissingRequirements() { return missingRequirements; }
    public void setMissingRequirements(List<String> missingRequirements) { this.missingRequirements = missingRequirements == null ? new ArrayList<>() : new ArrayList<>(missingRequirements); }
    public List<DispatchReadinessCheck> getChecks() { return checks; }
    public void setChecks(List<DispatchReadinessCheck> checks) { this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks); }
    public List<DispatchReadinessFixAction> getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(List<DispatchReadinessFixAction> recommendedActions) { this.recommendedActions = recommendedActions == null ? new ArrayList<>() : new ArrayList<>(recommendedActions); }
    public AgentSkillEvaluationResult getSkillEvaluation() { return skillEvaluation; }
    public void setSkillEvaluation(AgentSkillEvaluationResult skillEvaluation) { this.skillEvaluation = skillEvaluation; }
    public TaskDispatchContractResolveResult getContractResolution() { return contractResolution; }
    public void setContractResolution(TaskDispatchContractResolveResult contractResolution) { this.contractResolution = contractResolution; }
    public Map<String, Object> getLabels() { return labels; }
    public void setLabels(Map<String, Object> labels) { this.labels = labels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(labels); }
    public OffsetDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(OffsetDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
