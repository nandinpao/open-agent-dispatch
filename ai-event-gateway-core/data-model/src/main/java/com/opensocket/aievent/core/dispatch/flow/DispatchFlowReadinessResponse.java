package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2 Flow Rule dry-run result.
 *
 * This intentionally keeps ready/status/checks shape familiar to the Admin UI,
 * but the semantics are Flow -> Rule -> Skill -> Agent instead of legacy
 * Task Definition / Assignment Profile readiness.
 */
public class DispatchFlowReadinessResponse {
    private String tenantId;
    private String flowId;
    private String flowCode;
    private String ruleId;
    private String ruleCode;
    private String sourceSystem;
    private String originSourceSystem;
    private String targetSystem;
    private String eventStage;
    private String objectType;
    private String eventType;
    private String errorCode;
    private String requestedSkill;
    private String selectedAgentId;
    private Boolean ready = false;
    private Boolean dispatchable = false;
    private String status = "BLOCKED";
    private String summary;
    private String firstBlockingCode;
    private String firstBlockingReason;
    private List<DispatchFlowReadinessCheck> checks = new ArrayList<>();
    private List<String> requiredSkills = new ArrayList<>();
    private List<DispatchFlowCandidateAgentView> candidateAgents = new ArrayList<>();
    private Map<String, Object> diagnostics = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

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
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getOriginSourceSystem() { return originSourceSystem; }
    public void setOriginSourceSystem(String originSourceSystem) { this.originSourceSystem = originSourceSystem; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getSelectedAgentId() { return selectedAgentId; }
    public void setSelectedAgentId(String selectedAgentId) { this.selectedAgentId = selectedAgentId; }
    public Boolean getReady() { return ready; }
    public void setReady(Boolean ready) { this.ready = ready; }
    public Boolean getDispatchable() { return dispatchable; }
    public void setDispatchable(Boolean dispatchable) { this.dispatchable = dispatchable; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFirstBlockingCode() { return firstBlockingCode; }
    public void setFirstBlockingCode(String firstBlockingCode) { this.firstBlockingCode = firstBlockingCode; }
    public String getFirstBlockingReason() { return firstBlockingReason; }
    public void setFirstBlockingReason(String firstBlockingReason) { this.firstBlockingReason = firstBlockingReason; }
    public List<DispatchFlowReadinessCheck> getChecks() { return checks; }
    public void setChecks(List<DispatchFlowReadinessCheck> checks) { this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks); }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills == null ? new ArrayList<>() : new ArrayList<>(requiredSkills); }
    public List<DispatchFlowCandidateAgentView> getCandidateAgents() { return candidateAgents; }
    public void setCandidateAgents(List<DispatchFlowCandidateAgentView> candidateAgents) { this.candidateAgents = candidateAgents == null ? new ArrayList<>() : new ArrayList<>(candidateAgents); }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(Map<String, Object> diagnostics) { this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics); }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
