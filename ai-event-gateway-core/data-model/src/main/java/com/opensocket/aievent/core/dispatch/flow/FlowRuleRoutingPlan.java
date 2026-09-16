package com.opensocket.aievent.core.dispatch.flow;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/**
 * R6 routing plan resolved before legacy capability/profile fallback.
 *
 * This object is intentionally small: it carries the Flow-owned Rule evidence that
 * must be copied to Task / Assignment records before the legacy-safe fallback path
 * is removed in R9.
 */
public class FlowRuleRoutingPlan {
    private boolean matched;
    private FlowMatchDecision.MatchResult matchResult = FlowMatchDecision.MatchResult.NO_MATCH;
    private String flowEvaluationSetRef;
    private String flowId;
    private String flowVersion;
    private String serviceCode;
    private FlowRuleEvaluation evaluation;
    private Map<String,Object> matchAttributes = Map.of();
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
    private String issueSyncPolicy = "OPTIONAL";
    private String issueSyncPolicySource = "SYSTEM_FALLBACK";

    public static FlowRuleRoutingPlan notMatched(String reason) {
        FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
        plan.setMatched(false);
        plan.setMatchResult(FlowMatchDecision.MatchResult.NO_MATCH);
        plan.setRoutingPath("FLOW_RULE_REQUIRED_BLOCKED");
        plan.setReason(reason);
        return plan;
    }

    public static FlowRuleRoutingPlan ambiguous(String reason) {
        FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
        plan.setMatched(false);
        plan.setMatchResult(FlowMatchDecision.MatchResult.AMBIGUOUS);
        plan.setRoutingPath("FLOW_RULE_AMBIGUOUS");
        plan.setReason(reason);
        return plan;
    }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; if (matched) this.matchResult = FlowMatchDecision.MatchResult.MATCHED; }
    public FlowMatchDecision.MatchResult getMatchResult() { return matchResult; }
    public void setMatchResult(FlowMatchDecision.MatchResult matchResult) { this.matchResult = matchResult == null ? FlowMatchDecision.MatchResult.NO_MATCH : matchResult; this.matched = this.matchResult == FlowMatchDecision.MatchResult.MATCHED; }
    public boolean isAmbiguous() { return matchResult == FlowMatchDecision.MatchResult.AMBIGUOUS; }
    public boolean isNoMatch() { return matchResult == FlowMatchDecision.MatchResult.NO_MATCH; }
    public String getFlowEvaluationSetRef() { return flowEvaluationSetRef; }
    public void setFlowEvaluationSetRef(String flowEvaluationSetRef) { this.flowEvaluationSetRef = flowEvaluationSetRef; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getFlowVersion() { return flowVersion; }
    public void setFlowVersion(String flowVersion) { this.flowVersion = flowVersion; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public FlowRuleEvaluation getEvaluation() { return evaluation; }
    public void setEvaluation(FlowRuleEvaluation evaluation) { this.evaluation = evaluation; }
    public Map<String,Object> getMatchAttributes() { return matchAttributes; }
    public void setMatchAttributes(Map<String,Object> matchAttributes) {
        if (matchAttributes == null || matchAttributes.isEmpty()) { this.matchAttributes = Map.of(); return; }
        LinkedHashMap<String,Object> copy = new LinkedHashMap<>();
        matchAttributes.forEach((key,value) -> { if (key != null) copy.put(key, value); });
        this.matchAttributes = Collections.unmodifiableMap(copy);
    }
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
    public String getIssueSyncPolicy() { return issueSyncPolicy; }
    public void setIssueSyncPolicy(String issueSyncPolicy) { this.issueSyncPolicy = issueSyncPolicy; }
    public String getIssueSyncPolicySource() { return issueSyncPolicySource; }
    public void setIssueSyncPolicySource(String issueSyncPolicySource) { this.issueSyncPolicySource = issueSyncPolicySource; }
}
