package com.opensocket.aievent.core.routing.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source-agnostic requirement contract calculated by the P2 resolver.
 * This object is observational in P2 and never changes the authoritative routing result.
 */
public class DispatchRequirementResolution {
    private String tenantId;
    private String taskId;
    private String matchedFlowId;
    private String matchedRuleId;
    private String sourceSystem;
    private RequirementResolutionMode resolutionMode = RequirementResolutionMode.LEGACY;
    private RequirementDecisionStatus outcome = RequirementDecisionStatus.RESOLVED;
    private String sourceDefaultPolicySourceSystem;
    private String operationProfileId;
    private List<DispatchOperation> requiredOperations = new ArrayList<>();
    private List<String> requiredCapabilities = new ArrayList<>();
    private SideEffectLevel sideEffectLevel = SideEffectLevel.NONE;
    private CandidatePoolMode candidatePoolMode = CandidatePoolMode.LEGACY;
    private GenericRoutingStrategy routingStrategy = GenericRoutingStrategy.WEIGHTED_SCORE;
    private boolean explicitActionAuthorizationRequired = true;
    private String reasonCode;
    private int resolverVersion = 2;
    private Map<String, Object> details = new LinkedHashMap<>();

    public boolean isBlocked() {
        return outcome == RequirementDecisionStatus.BLOCKED;
    }

    public void validate() {
        require(tenantId, "tenantId");
        require(taskId, "taskId");
        require(sourceSystem, "sourceSystem");
        if (resolutionMode == null) throw new IllegalArgumentException("resolutionMode is required");
        if (outcome == null) throw new IllegalArgumentException("outcome is required");
        if (sideEffectLevel == null) throw new IllegalArgumentException("sideEffectLevel is required");
        if (candidatePoolMode == null) throw new IllegalArgumentException("candidatePoolMode is required");
        if (routingStrategy == null) throw new IllegalArgumentException("routingStrategy is required");
        if (resolverVersion < 1) throw new IllegalArgumentException("resolverVersion must be >= 1");
        if (sideEffectLevel.isEffectful() && !explicitActionAuthorizationRequired) {
            throw new IllegalArgumentException("effectful requirement must require explicit action authorization");
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public RequirementResolutionMode getResolutionMode() { return resolutionMode; }
    public void setResolutionMode(RequirementResolutionMode resolutionMode) { this.resolutionMode = resolutionMode; }
    public RequirementDecisionStatus getOutcome() { return outcome; }
    public void setOutcome(RequirementDecisionStatus outcome) { this.outcome = outcome; }
    public String getSourceDefaultPolicySourceSystem() { return sourceDefaultPolicySourceSystem; }
    public void setSourceDefaultPolicySourceSystem(String sourceDefaultPolicySourceSystem) { this.sourceDefaultPolicySourceSystem = sourceDefaultPolicySourceSystem; }
    public String getOperationProfileId() { return operationProfileId; }
    public void setOperationProfileId(String operationProfileId) { this.operationProfileId = operationProfileId; }
    public List<DispatchOperation> getRequiredOperations() { return new ArrayList<>(requiredOperations); }
    public void setRequiredOperations(List<DispatchOperation> requiredOperations) { this.requiredOperations = requiredOperations == null ? new ArrayList<>() : new ArrayList<>(requiredOperations); }
    public List<String> getRequiredCapabilities() { return new ArrayList<>(requiredCapabilities); }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities); }
    public SideEffectLevel getSideEffectLevel() { return sideEffectLevel; }
    public void setSideEffectLevel(SideEffectLevel sideEffectLevel) { this.sideEffectLevel = sideEffectLevel; }
    public CandidatePoolMode getCandidatePoolMode() { return candidatePoolMode; }
    public void setCandidatePoolMode(CandidatePoolMode candidatePoolMode) { this.candidatePoolMode = candidatePoolMode; }
    public GenericRoutingStrategy getRoutingStrategy() { return routingStrategy; }
    public void setRoutingStrategy(GenericRoutingStrategy routingStrategy) { this.routingStrategy = routingStrategy; }
    public boolean isExplicitActionAuthorizationRequired() { return explicitActionAuthorizationRequired; }
    public void setExplicitActionAuthorizationRequired(boolean explicitActionAuthorizationRequired) { this.explicitActionAuthorizationRequired = explicitActionAuthorizationRequired; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public int getResolverVersion() { return resolverVersion; }
    public void setResolverVersion(int resolverVersion) { this.resolverVersion = resolverVersion; }
    public Map<String, Object> getDetails() { return new LinkedHashMap<>(details); }
    public void setDetails(Map<String, Object> details) { this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details); }
}
