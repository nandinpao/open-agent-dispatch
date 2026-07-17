package com.opensocket.aievent.core.agent.skill;

import java.util.ArrayList;
import java.util.List;

public class AgentSkillEvaluationRequest {
    private String taskType;
    private String domain;
    private String provider;
    private String siteCode;
    private String operation;
    private String requiredToolPolicy;
    private List<String> requiredCapabilities = new ArrayList<>();
    private List<String> dataClasses = new ArrayList<>();

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
}
