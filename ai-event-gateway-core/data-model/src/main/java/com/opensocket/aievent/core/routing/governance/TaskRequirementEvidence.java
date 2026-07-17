package com.opensocket.aievent.core.routing.governance;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskRequirementEvidence {
    private String tenantId;
    private String evidenceId;
    private String taskId;
    private String assignmentId;
    private String matchedFlowId;
    private String matchedRuleId;
    private String sourceSystem;
    private RequirementResolutionMode resolutionMode = RequirementResolutionMode.LEGACY;
    private String sourceDefaultPolicySourceSystem;
    private String operationProfileId;
    private List<DispatchOperation> requiredOperations = new ArrayList<>();
    private List<String> requiredCapabilities = new ArrayList<>();
    private SideEffectLevel sideEffectLevel = SideEffectLevel.NONE;
    private CandidatePoolMode candidatePoolMode = CandidatePoolMode.LEGACY;
    private GenericRoutingStrategy routingStrategy = GenericRoutingStrategy.WEIGHTED_SCORE;
    private boolean explicitActionAuthorizationRequired = true;
    private RequirementDecisionStatus decisionStatus = RequirementDecisionStatus.SHADOW_ONLY;
    private String reasonCode;
    private int resolverVersion = 1;
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private String createdBy;

    public void validate() {
        require(tenantId, "tenantId");
        require(evidenceId, "evidenceId");
        require(taskId, "taskId");
        require(sourceSystem, "sourceSystem");
        if (resolutionMode == null) throw new IllegalArgumentException("resolutionMode is required");
        if (sideEffectLevel == null) throw new IllegalArgumentException("sideEffectLevel is required");
        if (candidatePoolMode == null) throw new IllegalArgumentException("candidatePoolMode is required");
        if (routingStrategy == null) throw new IllegalArgumentException("routingStrategy is required");
        if (decisionStatus == null) throw new IllegalArgumentException("decisionStatus is required");
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
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public RequirementResolutionMode getResolutionMode() { return resolutionMode; }
    public void setResolutionMode(RequirementResolutionMode resolutionMode) { this.resolutionMode = resolutionMode; }
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
    public RequirementDecisionStatus getDecisionStatus() { return decisionStatus; }
    public void setDecisionStatus(RequirementDecisionStatus decisionStatus) { this.decisionStatus = decisionStatus; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public int getResolverVersion() { return resolverVersion; }
    public void setResolverVersion(int resolverVersion) { this.resolverVersion = resolverVersion; }
    public Map<String, Object> getEvidence() { return new LinkedHashMap<>(evidence); }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
