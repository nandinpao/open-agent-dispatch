package com.opensocket.aievent.core.executionattempt;

import java.time.OffsetDateTime;

public class TaskExecutionAttempt {
    private String executionAttemptId;
    private String taskId;
    private String assignmentId;
    private String dispatchAttemptId;
    private String agentId;
    private String agentSessionId;
    private String leaseId;
    private String fencingToken;
    private int attemptNo;
    private TaskExecutionAttemptStatus status;
    private String resultCode;
    private String errorCode;
    private String errorMessage;
    private String callbackId;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime updatedAt;

    public String getExecutionAttemptId() { return executionAttemptId; }
    public void setExecutionAttemptId(String executionAttemptId) { this.executionAttemptId = executionAttemptId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getDispatchAttemptId() { return dispatchAttemptId; }
    public void setDispatchAttemptId(String dispatchAttemptId) { this.dispatchAttemptId = dispatchAttemptId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getLeaseId() { return leaseId; }
    public void setLeaseId(String leaseId) { this.leaseId = leaseId; }
    public String getFencingToken() { return fencingToken; }
    public void setFencingToken(String fencingToken) { this.fencingToken = fencingToken; }
    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int attemptNo) { this.attemptNo = Math.max(1, attemptNo); }
    public TaskExecutionAttemptStatus getStatus() { return status; }
    public void setStatus(TaskExecutionAttemptStatus status) { this.status = status; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCallbackId() { return callbackId; }
    public void setCallbackId(String callbackId) { this.callbackId = callbackId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
