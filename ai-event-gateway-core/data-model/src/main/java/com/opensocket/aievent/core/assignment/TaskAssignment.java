package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;

public class TaskAssignment {
    private String assignmentId;
    private String taskId;
    private String incidentId;
    private String agentId;
    private String agentType;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String siteId;
    private String eventStage;
    private String originSourceSystem;
    private String targetSystem;
    private String requestedSkill;
    private String correlationId;
    private String parentTaskId;
    private String handoffMode;
    private String matchedFlowId;
    private String matchedRuleId;
    private String assignedPoolId;
    private String targetPoolId;
    private String routingPath;
    private AssignmentStatus status;
    private String routingPolicy;
    private String routingDecisionId;
    private String dispatchAttemptId;
    private String leaseId;
    private String fencingToken;
    private OffsetDateTime leaseExpiresAt;
    private int score;
    private String reason;
    private boolean capacityReserved;
    private OffsetDateTime capacityReservedAt;
    private OffsetDateTime capacityReleasedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getOriginSourceSystem() { return originSourceSystem; }
    public void setOriginSourceSystem(String originSourceSystem) { this.originSourceSystem = originSourceSystem; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public String getHandoffMode() { return handoffMode; }
    public void setHandoffMode(String handoffMode) { this.handoffMode = handoffMode; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getAssignedPoolId() { return assignedPoolId; }
    public void setAssignedPoolId(String assignedPoolId) { this.assignedPoolId = assignedPoolId; }
    public String getTargetPoolId() { return targetPoolId; }
    public void setTargetPoolId(String targetPoolId) { this.targetPoolId = targetPoolId; }
    public String getRoutingPath() { return routingPath; }
    public void setRoutingPath(String routingPath) { this.routingPath = routingPath; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
    public String getRoutingPolicy() { return routingPolicy; }
    public void setRoutingPolicy(String routingPolicy) { this.routingPolicy = routingPolicy; }
    public String getRoutingDecisionId() { return routingDecisionId; }
    public void setRoutingDecisionId(String routingDecisionId) { this.routingDecisionId = routingDecisionId; }
    public String getDispatchAttemptId() { return dispatchAttemptId; }
    public void setDispatchAttemptId(String dispatchAttemptId) { this.dispatchAttemptId = dispatchAttemptId; }
    public String getLeaseId() { return leaseId; }
    public void setLeaseId(String leaseId) { this.leaseId = leaseId; }
    public String getFencingToken() { return fencingToken; }
    public void setFencingToken(String fencingToken) { this.fencingToken = fencingToken; }
    public OffsetDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(OffsetDateTime leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isCapacityReserved() { return capacityReserved; }
    public void setCapacityReserved(boolean capacityReserved) { this.capacityReserved = capacityReserved; }
    public OffsetDateTime getCapacityReservedAt() { return capacityReservedAt; }
    public void setCapacityReservedAt(OffsetDateTime capacityReservedAt) { this.capacityReservedAt = capacityReservedAt; }
    public OffsetDateTime getCapacityReleasedAt() { return capacityReleasedAt; }
    public void setCapacityReleasedAt(OffsetDateTime capacityReleasedAt) { this.capacityReleasedAt = capacityReleasedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
