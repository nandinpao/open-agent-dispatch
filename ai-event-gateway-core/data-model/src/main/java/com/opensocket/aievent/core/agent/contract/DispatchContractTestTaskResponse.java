package com.opensocket.aievent.core.agent.contract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.task.evidence.TaskDispatchEvidenceView;

public class DispatchContractTestTaskResponse {
    private String tenantId;
    private String sourceSystem;
    private String taskType;
    private String agentId;
    private String status;
    private String summary;
    private DispatchContractReadinessResponse readinessBefore;
    private DispatchContractBootstrapResponse bootstrap;
    private DispatchContractReadinessResponse readinessAfter;
    private EventIntakeDecisionResponse eventDecision;
    private String taskId;
    private boolean taskCreated;
    private boolean assignmentCreated;
    private boolean dispatchRequestCreated;
    private String selectedAgentId;
    private TaskDispatchEvidenceView evidence;
    private List<String> nextActions = List.of();
    private Map<String, Object> diagnostics;
    private OffsetDateTime generatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public DispatchContractReadinessResponse getReadinessBefore() { return readinessBefore; }
    public void setReadinessBefore(DispatchContractReadinessResponse readinessBefore) { this.readinessBefore = readinessBefore; }
    public DispatchContractBootstrapResponse getBootstrap() { return bootstrap; }
    public void setBootstrap(DispatchContractBootstrapResponse bootstrap) { this.bootstrap = bootstrap; }
    public DispatchContractReadinessResponse getReadinessAfter() { return readinessAfter; }
    public void setReadinessAfter(DispatchContractReadinessResponse readinessAfter) { this.readinessAfter = readinessAfter; }
    public EventIntakeDecisionResponse getEventDecision() { return eventDecision; }
    public void setEventDecision(EventIntakeDecisionResponse eventDecision) { this.eventDecision = eventDecision; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public boolean isTaskCreated() { return taskCreated; }
    public void setTaskCreated(boolean taskCreated) { this.taskCreated = taskCreated; }
    public boolean isAssignmentCreated() { return assignmentCreated; }
    public void setAssignmentCreated(boolean assignmentCreated) { this.assignmentCreated = assignmentCreated; }
    public boolean isDispatchRequestCreated() { return dispatchRequestCreated; }
    public void setDispatchRequestCreated(boolean dispatchRequestCreated) { this.dispatchRequestCreated = dispatchRequestCreated; }
    public String getSelectedAgentId() { return selectedAgentId; }
    public void setSelectedAgentId(String selectedAgentId) { this.selectedAgentId = selectedAgentId; }
    public TaskDispatchEvidenceView getEvidence() { return evidence; }
    public void setEvidence(TaskDispatchEvidenceView evidence) { this.evidence = evidence; }
    public List<String> getNextActions() { return nextActions; }
    public void setNextActions(List<String> nextActions) { this.nextActions = nextActions == null ? List.of() : List.copyOf(nextActions); }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(Map<String, Object> diagnostics) { this.diagnostics = diagnostics; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
