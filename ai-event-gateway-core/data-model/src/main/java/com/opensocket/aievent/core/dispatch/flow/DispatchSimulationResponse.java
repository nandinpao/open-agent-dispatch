package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * No-side-effect simulation result for Source Flow -> Agent Pool routing.
 *
 * <p>createdArtifacts intentionally stays empty when the contract is respected.</p>
 */
public class DispatchSimulationResponse {
    private String tenantId;
    private String sourceSystem;
    private String eventStage;
    private String objectType;
    private String eventType;
    private String errorCode;
    private String matchedFlowId;
    private String flowVersion;
    private String evaluationMode;
    private String matchedRuleId;
    private String resolutionType;
    private String targetPoolId;
    private String targetPoolCode;
    private String selectionStrategy;
    private Integer poolMemberCount = 0;
    private Integer candidateAgentCount = 0;
    private Integer eligibleAgentCount = 0;
    private String selectedAgentId;
    private Boolean manualOnly = false;
    private Boolean dispatchable = false;
    private String status = "BLOCKED";
    private String summary;
    private String blockerCode;
    private String blockerReason;
    private Boolean sideEffectFree = true;
    private List<String> createdArtifacts = new ArrayList<>();
    private List<DispatchSimulationCandidateView> candidateEvidence = new ArrayList<>();
    private List<DispatchSimulationCandidateView> blockedCandidates = new ArrayList<>();
    private Map<String, Object> diagnostics = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getFlowVersion() { return flowVersion; }
    public void setFlowVersion(String flowVersion) { this.flowVersion = flowVersion; }
    public String getEvaluationMode() { return evaluationMode; }
    public void setEvaluationMode(String evaluationMode) { this.evaluationMode = evaluationMode; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getResolutionType() { return resolutionType; }
    public void setResolutionType(String resolutionType) { this.resolutionType = resolutionType; }
    public String getTargetPoolId() { return targetPoolId; }
    public void setTargetPoolId(String targetPoolId) { this.targetPoolId = targetPoolId; }
    public String getTargetPoolCode() { return targetPoolCode; }
    public void setTargetPoolCode(String targetPoolCode) { this.targetPoolCode = targetPoolCode; }
    public String getSelectionStrategy() { return selectionStrategy; }
    public void setSelectionStrategy(String selectionStrategy) { this.selectionStrategy = selectionStrategy; }
    public Integer getPoolMemberCount() { return poolMemberCount; }
    public void setPoolMemberCount(Integer poolMemberCount) { this.poolMemberCount = poolMemberCount; }
    public Integer getCandidateAgentCount() { return candidateAgentCount; }
    public void setCandidateAgentCount(Integer candidateAgentCount) { this.candidateAgentCount = candidateAgentCount; }
    public Integer getEligibleAgentCount() { return eligibleAgentCount; }
    public void setEligibleAgentCount(Integer eligibleAgentCount) { this.eligibleAgentCount = eligibleAgentCount; }
    public String getSelectedAgentId() { return selectedAgentId; }
    public void setSelectedAgentId(String selectedAgentId) { this.selectedAgentId = selectedAgentId; }
    public Boolean getManualOnly() { return manualOnly; }
    public void setManualOnly(Boolean manualOnly) { this.manualOnly = manualOnly; }
    public Boolean getDispatchable() { return dispatchable; }
    public void setDispatchable(Boolean dispatchable) { this.dispatchable = dispatchable; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBlockerCode() { return blockerCode; }
    public void setBlockerCode(String blockerCode) { this.blockerCode = blockerCode; }
    public String getBlockerReason() { return blockerReason; }
    public void setBlockerReason(String blockerReason) { this.blockerReason = blockerReason; }
    public Boolean getSideEffectFree() { return sideEffectFree; }
    public void setSideEffectFree(Boolean sideEffectFree) { this.sideEffectFree = sideEffectFree; }
    public List<String> getCreatedArtifacts() { return createdArtifacts; }
    public void setCreatedArtifacts(List<String> createdArtifacts) { this.createdArtifacts = createdArtifacts == null ? new ArrayList<>() : new ArrayList<>(createdArtifacts); }
    public List<DispatchSimulationCandidateView> getCandidateEvidence() { return candidateEvidence; }
    public void setCandidateEvidence(List<DispatchSimulationCandidateView> candidateEvidence) { this.candidateEvidence = candidateEvidence == null ? new ArrayList<>() : new ArrayList<>(candidateEvidence); }
    public List<DispatchSimulationCandidateView> getBlockedCandidates() { return blockedCandidates; }
    public void setBlockedCandidates(List<DispatchSimulationCandidateView> blockedCandidates) { this.blockedCandidates = blockedCandidates == null ? new ArrayList<>() : new ArrayList<>(blockedCandidates); }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(Map<String, Object> diagnostics) { this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics); }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
