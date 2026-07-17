package com.opensocket.aievent.core.routing.governance.eligibility;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.opensocket.aievent.core.routing.governance.RequirementResolutionMode;

/** Append-only P3 comparison between authoritative legacy eligibility and generic shadow eligibility. */
public class TaskAgentEligibilityShadowComparison {
    private String tenantId;
    private String comparisonId;
    private String requirementEvidenceId;
    private String taskId;
    private String matchedFlowId;
    private String matchedRuleId;
    private String sourceSystem;
    private String agentId;
    private RequirementResolutionMode shadowResolutionMode = RequirementResolutionMode.LEGACY;
    private boolean legacyCandidate;
    private boolean legacyEligible;
    private Integer legacyScore;
    private boolean shadowEligible;
    private EligibilityShadowDifferenceType differenceType = EligibilityShadowDifferenceType.EQUIVALENT_BLOCKED;
    private List<String> blockingReasonCodes = new ArrayList<>();
    private List<AgentEligibilityShadowCheck> checks = new ArrayList<>();
    private int evaluatorVersion = 3;
    private OffsetDateTime createdAt;
    private String createdBy;

    public void validate() {
        require(tenantId, "tenantId");
        require(comparisonId, "comparisonId");
        require(requirementEvidenceId, "requirementEvidenceId");
        require(taskId, "taskId");
        require(sourceSystem, "sourceSystem");
        require(agentId, "agentId");
        if (shadowResolutionMode == null) throw new IllegalArgumentException("shadowResolutionMode is required");
        if (differenceType == null) throw new IllegalArgumentException("differenceType is required");
        if (evaluatorVersion < 3) throw new IllegalArgumentException("evaluatorVersion must be >= 3");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getComparisonId() { return comparisonId; }
    public void setComparisonId(String comparisonId) { this.comparisonId = comparisonId; }
    public String getRequirementEvidenceId() { return requirementEvidenceId; }
    public void setRequirementEvidenceId(String requirementEvidenceId) { this.requirementEvidenceId = requirementEvidenceId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public RequirementResolutionMode getShadowResolutionMode() { return shadowResolutionMode; }
    public void setShadowResolutionMode(RequirementResolutionMode shadowResolutionMode) { this.shadowResolutionMode = shadowResolutionMode; }
    public boolean isLegacyCandidate() { return legacyCandidate; }
    public void setLegacyCandidate(boolean legacyCandidate) { this.legacyCandidate = legacyCandidate; }
    public boolean isLegacyEligible() { return legacyEligible; }
    public void setLegacyEligible(boolean legacyEligible) { this.legacyEligible = legacyEligible; }
    public Integer getLegacyScore() { return legacyScore; }
    public void setLegacyScore(Integer legacyScore) { this.legacyScore = legacyScore; }
    public boolean isShadowEligible() { return shadowEligible; }
    public void setShadowEligible(boolean shadowEligible) { this.shadowEligible = shadowEligible; }
    public EligibilityShadowDifferenceType getDifferenceType() { return differenceType; }
    public void setDifferenceType(EligibilityShadowDifferenceType differenceType) { this.differenceType = differenceType; }
    public List<String> getBlockingReasonCodes() { return new ArrayList<>(blockingReasonCodes); }
    public void setBlockingReasonCodes(List<String> blockingReasonCodes) { this.blockingReasonCodes = blockingReasonCodes == null ? new ArrayList<>() : new ArrayList<>(blockingReasonCodes); }
    public List<AgentEligibilityShadowCheck> getChecks() { return new ArrayList<>(checks); }
    public void setChecks(List<AgentEligibilityShadowCheck> checks) { this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks); }
    public int getEvaluatorVersion() { return evaluatorVersion; }
    public void setEvaluatorVersion(int evaluatorVersion) { this.evaluatorVersion = evaluatorVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
