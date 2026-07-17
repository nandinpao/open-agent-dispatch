package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class TaskDispatchContractResolveResult {
    private String taxonomyVersion;
    private String taskType;
    private String domain;
    private String provider;
    private String siteCode;
    private String operation;
    private String requiredToolPolicy;
    private List<String> requiredCapabilities = new ArrayList<>();
    private List<String> dataClasses = new ArrayList<>();
    private List<String> matchedSkillCodes = new ArrayList<>();
    private List<String> resolutionReasons = new ArrayList<>();
    private boolean resolved;
    private OffsetDateTime resolvedAt;

    public String getTaxonomyVersion() { return taxonomyVersion; }
    public void setTaxonomyVersion(String taxonomyVersion) { this.taxonomyVersion = taxonomyVersion; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getRequiredToolPolicy() { return requiredToolPolicy; }
    public void setRequiredToolPolicy(String requiredToolPolicy) { this.requiredToolPolicy = requiredToolPolicy; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities); }
    public List<String> getDataClasses() { return dataClasses; }
    public void setDataClasses(List<String> dataClasses) { this.dataClasses = dataClasses == null ? new ArrayList<>() : new ArrayList<>(dataClasses); }
    public List<String> getMatchedSkillCodes() { return matchedSkillCodes; }
    public void setMatchedSkillCodes(List<String> matchedSkillCodes) { this.matchedSkillCodes = matchedSkillCodes == null ? new ArrayList<>() : new ArrayList<>(matchedSkillCodes); }
    public List<String> getResolutionReasons() { return resolutionReasons; }
    public void setResolutionReasons(List<String> resolutionReasons) { this.resolutionReasons = resolutionReasons == null ? new ArrayList<>() : new ArrayList<>(resolutionReasons); }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public AgentSkillEvaluationRequest toEvaluationRequest() {
        AgentSkillEvaluationRequest request = new AgentSkillEvaluationRequest();
        request.setDomain(domain);
        request.setProvider(provider);
        request.setSiteCode(siteCode);
        request.setTaskType(taskType);
        request.setOperation(operation);
        request.setRequiredToolPolicy(requiredToolPolicy);
        request.setRequiredCapabilities(matchedSkillCodes == null || matchedSkillCodes.isEmpty() ? requiredCapabilities : matchedSkillCodes);
        request.setDataClasses(dataClasses);
        return request;
    }
}
