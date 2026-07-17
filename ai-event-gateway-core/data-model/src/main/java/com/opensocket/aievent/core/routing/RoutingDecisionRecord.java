package com.opensocket.aievent.core.routing;

import java.time.OffsetDateTime;
import java.util.List;

public class RoutingDecisionRecord {
    private String decisionId;
    private String taskId;
    private String incidentId;
    private RoutingPolicy routingPolicy;
    private RoutingDecisionStatus status;
    private String selectedAgentId;
    private String selectedGatewayNodeId;
    private String selectedAgentSessionId;
    private String selectedSiteId;
    private int selectedScore;
    private String decisionReason;
    private DispatchUserFacingError userFacingError;
    private List<AgentCandidateScore> candidates = List.of();
    private OffsetDateTime createdAt;

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public RoutingPolicy getRoutingPolicy() { return routingPolicy; }
    public void setRoutingPolicy(RoutingPolicy routingPolicy) { this.routingPolicy = routingPolicy; }
    public RoutingDecisionStatus getStatus() { return status; }
    public void setStatus(RoutingDecisionStatus status) { this.status = status; }
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
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public DispatchUserFacingError getUserFacingError() { return userFacingError; }
    public void setUserFacingError(DispatchUserFacingError userFacingError) { this.userFacingError = userFacingError; }
    public List<AgentCandidateScore> getCandidates() { return candidates; }
    public void setCandidates(List<AgentCandidateScore> candidates) { this.candidates = candidates == null ? List.of() : List.copyOf(candidates); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
