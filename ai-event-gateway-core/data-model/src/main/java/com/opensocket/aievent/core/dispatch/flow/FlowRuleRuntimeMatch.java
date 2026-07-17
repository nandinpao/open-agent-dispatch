package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;

/**
 * Runtime match result for persisted Flow-owned Dispatch Rules.
 *
 * R12.9 closes the R6/R9 gap: the runtime resolver must read persisted
 * dispatch_flows / dispatch_policies / flow_required_capabilities instead of expecting
 * TaskRecord to already carry matchedFlowId, matchedRuleId, and requestedSkill.
 */
public class FlowRuleRuntimeMatch {
    private String tenantId;
    private String flowId;
    private String flowCode;
    private String ruleId;
    private String ruleCode;
    private String ruleScope;
    private String eventStage;
    private String sourceSystem;
    private String originSourceSystem;
    private String targetSystem;
    private String eventType;
    private String objectType;
    private String errorCode;
    private String requestedSkill;
    private String handoffMode;
    private List<String> requiredSkills = List.of();
    private String capabilityRequirementMode = "LEGACY";
    private String requiredOperation;
    private String sideEffectLevel = "NONE";
    private String candidatePoolMode = "LEGACY";
    private String routingStrategy = "WEIGHTED_SCORE";
    private String targetPoolId;
    private String targetPoolCode;
    private String defaultPoolId;
    private String selectionStrategy = "LOWEST_LOAD";
    private boolean sourceDefaultPool;
    private Boolean explicitActionAuthorizationRequired = Boolean.TRUE;
    private Integer requirementModelVersion = 1;
    private String matchReason;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
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
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getHandoffMode() { return handoffMode; }
    public void setHandoffMode(String handoffMode) { this.handoffMode = handoffMode; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills); }
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
    public String getTargetPoolId() { return targetPoolId; }
    public void setTargetPoolId(String targetPoolId) { this.targetPoolId = targetPoolId; }
    public String getTargetPoolCode() { return targetPoolCode; }
    public void setTargetPoolCode(String targetPoolCode) { this.targetPoolCode = targetPoolCode; }
    public String getDefaultPoolId() { return defaultPoolId; }
    public void setDefaultPoolId(String defaultPoolId) { this.defaultPoolId = defaultPoolId; }
    public String getSelectionStrategy() { return selectionStrategy; }
    public void setSelectionStrategy(String selectionStrategy) { this.selectionStrategy = selectionStrategy; }
    public boolean isSourceDefaultPool() { return sourceDefaultPool; }
    public void setSourceDefaultPool(boolean sourceDefaultPool) { this.sourceDefaultPool = sourceDefaultPool; }
    public Boolean getExplicitActionAuthorizationRequired() { return explicitActionAuthorizationRequired; }
    public void setExplicitActionAuthorizationRequired(Boolean explicitActionAuthorizationRequired) { this.explicitActionAuthorizationRequired = explicitActionAuthorizationRequired; }
    public Integer getRequirementModelVersion() { return requirementModelVersion; }
    public void setRequirementModelVersion(Integer requirementModelVersion) { this.requirementModelVersion = requirementModelVersion; }
    public String getMatchReason() { return matchReason; }
    public void setMatchReason(String matchReason) { this.matchReason = matchReason; }
}
