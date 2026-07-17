package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DispatchFlowView {
    private String tenantId;
    private String flowId;
    private String flowCode;
    private String flowName;
    private String sourceSystem;
    private String flowType = "SOURCE_FLOW";
    private String defaultPoolId;
    private String status = "DRAFT";
    private String description;
    private String defaultCapabilityRequirementMode = "NONE";
    private String defaultRequiredOperation = "ANALYZE";
    private String defaultSideEffectLevel = "NONE";
    private String defaultCandidatePoolMode = "EXPLICIT_FLOW_AGENTS";
    private String defaultRoutingStrategy = "WEIGHTED_SCORE";
    private Integer externalRuleCount = 0;
    private Integer a2aRuleCount = 0;
    private Integer skillCount = 0;
    private Integer agentCount = 0;
    private String lastTestStatus;
    private List<DispatchFlowRuleView> rules = new ArrayList<>();
    private List<DispatchFlowRequiredSkillView> requiredSkills = new ArrayList<>();
    private List<DispatchFlowAgentView> agents = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime updatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }
    public String getDefaultPoolId() { return defaultPoolId; }
    public void setDefaultPoolId(String defaultPoolId) { this.defaultPoolId = defaultPoolId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDefaultCapabilityRequirementMode() { return defaultCapabilityRequirementMode; }
    public void setDefaultCapabilityRequirementMode(String defaultCapabilityRequirementMode) { this.defaultCapabilityRequirementMode = defaultCapabilityRequirementMode; }
    public String getDefaultRequiredOperation() { return defaultRequiredOperation; }
    public void setDefaultRequiredOperation(String defaultRequiredOperation) { this.defaultRequiredOperation = defaultRequiredOperation; }
    public String getDefaultSideEffectLevel() { return defaultSideEffectLevel; }
    public void setDefaultSideEffectLevel(String defaultSideEffectLevel) { this.defaultSideEffectLevel = defaultSideEffectLevel; }
    public String getDefaultCandidatePoolMode() { return defaultCandidatePoolMode; }
    public void setDefaultCandidatePoolMode(String defaultCandidatePoolMode) { this.defaultCandidatePoolMode = defaultCandidatePoolMode; }
    public String getDefaultRoutingStrategy() { return defaultRoutingStrategy; }
    public void setDefaultRoutingStrategy(String defaultRoutingStrategy) { this.defaultRoutingStrategy = defaultRoutingStrategy; }
    public Integer getExternalRuleCount() { return externalRuleCount; }
    public void setExternalRuleCount(Integer externalRuleCount) { this.externalRuleCount = externalRuleCount; }
    public Integer getA2aRuleCount() { return a2aRuleCount; }
    public void setA2aRuleCount(Integer a2aRuleCount) { this.a2aRuleCount = a2aRuleCount; }
    public Integer getSkillCount() { return skillCount; }
    public void setSkillCount(Integer skillCount) { this.skillCount = skillCount; }
    public Integer getCapabilityCount() { return skillCount; }
    public void setCapabilityCount(Integer capabilityCount) { this.skillCount = capabilityCount; }
    public Integer getAgentCount() { return agentCount; }
    public void setAgentCount(Integer agentCount) { this.agentCount = agentCount; }
    public String getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(String lastTestStatus) { this.lastTestStatus = lastTestStatus; }
    public List<DispatchFlowRuleView> getRules() { return rules; }
    public void setRules(List<DispatchFlowRuleView> rules) { this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules); }
    public List<DispatchFlowRequiredSkillView> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<DispatchFlowRequiredSkillView> requiredSkills) { this.requiredSkills = requiredSkills == null ? new ArrayList<>() : new ArrayList<>(requiredSkills); }
    public List<DispatchFlowRequiredSkillView> getRequiredCapabilities() { return requiredSkills; }
    public void setRequiredCapabilities(List<DispatchFlowRequiredSkillView> requiredCapabilities) { this.requiredSkills = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities); }
    public List<DispatchFlowAgentView> getAgents() { return agents; }
    public void setAgents(List<DispatchFlowAgentView> agents) { this.agents = agents == null ? new ArrayList<>() : new ArrayList<>(agents); }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
