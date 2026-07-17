package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;

/**
 * R6 routing plan resolved before legacy capability/profile fallback.
 *
 * This object is intentionally small: it carries the Flow-owned Rule evidence that
 * must be copied to Task / Assignment records before the legacy-safe fallback path
 * is removed in R9.
 */
public class FlowRuleRoutingPlan {
    private boolean matched;
    private String flowId;
    private String ruleId;
    private String ruleScope;
    private String eventStage;
    private String requestedSkill;
    private String targetSystem;
    private String handoffMode;
    private String routingPath;
    private String reason;
    private List<String> requiredSkills = List.of();
    private String capabilityRequirementMode = "NONE";
    private String requiredOperation;
    private String sideEffectLevel = "NONE";
    private String candidatePoolMode = "EXPLICIT_FLOW_AGENTS";
    private String routingStrategy = "WEIGHTED_SCORE";
    private String targetPoolId;
    private String targetPoolCode;
    private String defaultPoolId;
    private String selectionStrategy = "LOWEST_LOAD";
    private boolean sourceDefaultPool;
    private Boolean explicitActionAuthorizationRequired = Boolean.TRUE;
    private Integer requirementModelVersion = 3;

    public static FlowRuleRoutingPlan notMatched(String reason) {
        FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
        plan.setMatched(false);
        plan.setRoutingPath("FLOW_RULE_REQUIRED_BLOCKED");
        plan.setReason(reason);
        return plan;
    }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleScope() { return ruleScope; }
    public void setRuleScope(String ruleScope) { this.ruleScope = ruleScope; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getHandoffMode() { return handoffMode; }
    public void setHandoffMode(String handoffMode) { this.handoffMode = handoffMode; }
    public String getRoutingPath() { return routingPath; }
    public void setRoutingPath(String routingPath) { this.routingPath = routingPath; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
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
}
