package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class DispatchFlowRuleView {
    private String tenantId;
    private String ruleId;
    private String flowId;
    private String ruleCode;
    private String ruleName;
    private String ruleScope;
    private String eventStage;
    private String sourceSystem;
    private String originSourceSystem;
    private String targetSystem;
    private String eventType;
    private String objectType;
    private String errorCode;
    private Map<String, Object> condition = new LinkedHashMap<>();
    private String matchMode = "EXACT_OR_WILDCARD";
    private String targetPoolId;
    private String targetPoolCode;
    private String requestedSkill;
    private String capabilityRequirementMode;
    private String requiredOperation = "ANALYZE";
    private String sideEffectLevel = "NONE";
    private String candidatePoolMode = "EXPLICIT_FLOW_AGENTS";
    private String routingStrategy = "WEIGHTED_SCORE";
    private Boolean explicitActionAuthorizationRequired = Boolean.TRUE;
    private Integer requirementModelVersion = 10;
    private String handoffMode;
    private String issuePolicyId;
    private Integer priority = 100;
    private Boolean enabled = Boolean.FALSE;
    private String legacyStatus = "LEGACY_READONLY";
    private OffsetDateTime updatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleScope() { return ruleScope; }
    public void setRuleScope(String ruleScope) { this.ruleScope = ruleScope; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getOriginSourceSystem() { return originSourceSystem; }
    public void setOriginSourceSystem(String originSourceSystem) { this.originSourceSystem = originSourceSystem; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Map<String, Object> getCondition() { return condition; }
    public void setCondition(Map<String, Object> condition) { this.condition = condition == null ? new LinkedHashMap<>() : new LinkedHashMap<>(condition); }
    public String getMatchMode() { return matchMode; }
    public void setMatchMode(String matchMode) { this.matchMode = matchMode; }
    public String getTargetPoolId() { return targetPoolId; }
    public void setTargetPoolId(String targetPoolId) { this.targetPoolId = targetPoolId; }
    public String getTargetPoolCode() { return targetPoolCode; }
    public void setTargetPoolCode(String targetPoolCode) { this.targetPoolCode = targetPoolCode; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getCapabilityRequirementMode() { return capabilityRequirementMode; }
    public void setCapabilityRequirementMode(String capabilityRequirementMode) { this.capabilityRequirementMode = capabilityRequirementMode; }
    public String getRequiredOperation() { return requiredOperation; }
    public void setRequiredOperation(String requiredOperation) { this.requiredOperation = requiredOperation; }
    public String getSideEffectLevel() { return sideEffectLevel; }
    public void setSideEffectLevel(String sideEffectLevel) { this.sideEffectLevel = sideEffectLevel; }
    public String getCandidatePoolMode() { return candidatePoolMode; }
    public void setCandidatePoolMode(String candidatePoolMode) { this.candidatePoolMode = candidatePoolMode; }
    public String getRoutingStrategy() { return routingStrategy; }
    public void setRoutingStrategy(String routingStrategy) { this.routingStrategy = routingStrategy; }
    public Boolean getExplicitActionAuthorizationRequired() { return explicitActionAuthorizationRequired; }
    public void setExplicitActionAuthorizationRequired(Boolean explicitActionAuthorizationRequired) { this.explicitActionAuthorizationRequired = explicitActionAuthorizationRequired; }
    public Integer getRequirementModelVersion() { return requirementModelVersion; }
    public void setRequirementModelVersion(Integer requirementModelVersion) { this.requirementModelVersion = requirementModelVersion; }
    public String getHandoffMode() { return handoffMode; }
    public void setHandoffMode(String handoffMode) { this.handoffMode = handoffMode; }
    public String getIssuePolicyId() { return issuePolicyId; }
    public void setIssuePolicyId(String issuePolicyId) { this.issuePolicyId = issuePolicyId; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getLegacyStatus() { return legacyStatus; }
    public void setLegacyStatus(String legacyStatus) { this.legacyStatus = legacyStatus; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
