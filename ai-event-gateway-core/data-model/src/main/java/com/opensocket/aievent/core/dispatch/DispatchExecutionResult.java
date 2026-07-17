package com.opensocket.aievent.core.dispatch;

public class DispatchExecutionResult {
    private String dispatchRequestId;
    private String taskId;
    private String assignmentId;
    private String agentId;
    private String ownerGatewayNodeId;
    private boolean executed;
    private String dispatchStatus;
    private String taskStatus;
    private String gatewayStatus;
    private String message;

    public static DispatchExecutionResult skipped(DispatchRequest request, String message) {
        DispatchExecutionResult result = from(request);
        result.setExecuted(false);
        result.setMessage(message);
        return result;
    }

    public static DispatchExecutionResult from(DispatchRequest request) {
        DispatchExecutionResult result = new DispatchExecutionResult();
        if (request != null) {
            result.setDispatchRequestId(request.getDispatchRequestId());
            result.setTaskId(request.getTaskId());
            result.setAssignmentId(request.getAssignmentId());
            result.setAgentId(request.getAgentId());
            result.setOwnerGatewayNodeId(request.getOwnerGatewayNodeId());
            result.setDispatchStatus(request.getStatus() == null ? null : request.getStatus().name());
        }
        return result;
    }

    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getOwnerGatewayNodeId() { return ownerGatewayNodeId; }
    public void setOwnerGatewayNodeId(String ownerGatewayNodeId) { this.ownerGatewayNodeId = ownerGatewayNodeId; }
    public boolean isExecuted() { return executed; }
    public void setExecuted(boolean executed) { this.executed = executed; }
    public String getDispatchStatus() { return dispatchStatus; }
    public void setDispatchStatus(String dispatchStatus) { this.dispatchStatus = dispatchStatus; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getGatewayStatus() { return gatewayStatus; }
    public void setGatewayStatus(String gatewayStatus) { this.gatewayStatus = gatewayStatus; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
