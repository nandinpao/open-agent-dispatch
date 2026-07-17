package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class TaskDispatchAttempt {
    private String dispatchAttemptId;
    private String taskId;
    private String incidentId;
    private String routingDecisionId;
    private String selectedAgentId;
    private String selectedGatewayNodeId;
    private String selectedAgentSessionId;
    private String selectedSiteId;
    private int selectedScore;
    private TaskDispatchAttemptStatus status;
    private DispatchEligibilityStatus eligibilityStatus;
    private String decisionReason;
    private Map<String, Object> scoreBreakdown = Map.of();
    private Map<String, Object> eligibilityFacts = Map.of();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getDispatchAttemptId() { return dispatchAttemptId; }
    public void setDispatchAttemptId(String dispatchAttemptId) { this.dispatchAttemptId = dispatchAttemptId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getRoutingDecisionId() { return routingDecisionId; }
    public void setRoutingDecisionId(String routingDecisionId) { this.routingDecisionId = routingDecisionId; }
    public String getSelectedAgentId() { return selectedAgentId; }
    public void setSelectedAgentId(String selectedAgentId) { this.selectedAgentId = selectedAgentId; }
    public String getSelectedGatewayNodeId() { return selectedGatewayNodeId; }
    public void setSelectedGatewayNodeId(String selectedGatewayNodeId) { this.selectedGatewayNodeId = selectedGatewayNodeId; }
    public String getSelectedAgentSessionId() { return selectedAgentSessionId; }
    public void setSelectedAgentSessionId(String selectedAgentSessionId) { this.selectedAgentSessionId = selectedAgentSessionId; }
    public String getSelectedSiteId() { return selectedSiteId; }
    public void setSelectedSiteId(String selectedSiteId) { this.selectedSiteId = selectedSiteId; }
    public int getSelectedScore() { return selectedScore; }
    public void setSelectedScore(int selectedScore) { this.selectedScore = selectedScore; }
    public TaskDispatchAttemptStatus getStatus() { return status; }
    public void setStatus(TaskDispatchAttemptStatus status) { this.status = status; }
    public DispatchEligibilityStatus getEligibilityStatus() { return eligibilityStatus; }
    public void setEligibilityStatus(DispatchEligibilityStatus eligibilityStatus) { this.eligibilityStatus = eligibilityStatus; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Map<String, Object> getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(Map<String, Object> scoreBreakdown) { this.scoreBreakdown = scoreBreakdown == null ? Map.of() : new LinkedHashMap<>(scoreBreakdown); }
    public Map<String, Object> getEligibilityFacts() { return eligibilityFacts; }
    public void setEligibilityFacts(Map<String, Object> eligibilityFacts) { this.eligibilityFacts = eligibilityFacts == null ? Map.of() : new LinkedHashMap<>(eligibilityFacts); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
