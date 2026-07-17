package com.opensocket.aievent.core.dispatch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NettyDispatchCommand {
    private String taskId;
    private String assignmentId;
    private String dispatchRequestId;
    private int attemptNo;
    private String targetAgentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String sourceNodeId;
    private String dispatchToken;
    private String fencingToken;
    private String incidentId;
    private String taskType;
    private String priority;
    private String routingPolicy;
    private List<String> requiredCapabilities = List.of();
    private Map<String, Object> input = new LinkedHashMap<>();

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int attemptNo) { this.attemptNo = attemptNo; }
    public String getTargetAgentId() { return targetAgentId; }
    public void setTargetAgentId(String targetAgentId) { this.targetAgentId = targetAgentId; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public String getDispatchToken() { return dispatchToken; }
    public void setDispatchToken(String dispatchToken) { this.dispatchToken = dispatchToken; }
    public String getFencingToken() { return fencingToken; }
    public void setFencingToken(String fencingToken) { this.fencingToken = fencingToken; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getRoutingPolicy() { return routingPolicy; }
    public void setRoutingPolicy(String routingPolicy) { this.routingPolicy = routingPolicy; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities); }
    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input == null ? new LinkedHashMap<>() : new LinkedHashMap<>(input); }
}
