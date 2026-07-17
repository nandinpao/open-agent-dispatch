package com.opensocket.aievent.core.callback;

public class DispatchRecoveryResult {
    private String dispatchRequestId;
    private String taskId;
    private String previousDispatchStatus;
    private String newDispatchStatus;
    private String previousTaskStatus;
    private String newTaskStatus;
    private boolean timedOut;
    private boolean retryScheduled;
    private String message;

    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getPreviousDispatchStatus() { return previousDispatchStatus; }
    public void setPreviousDispatchStatus(String previousDispatchStatus) { this.previousDispatchStatus = previousDispatchStatus; }
    public String getNewDispatchStatus() { return newDispatchStatus; }
    public void setNewDispatchStatus(String newDispatchStatus) { this.newDispatchStatus = newDispatchStatus; }
    public String getPreviousTaskStatus() { return previousTaskStatus; }
    public void setPreviousTaskStatus(String previousTaskStatus) { this.previousTaskStatus = previousTaskStatus; }
    public String getNewTaskStatus() { return newTaskStatus; }
    public void setNewTaskStatus(String newTaskStatus) { this.newTaskStatus = newTaskStatus; }
    public boolean isTimedOut() { return timedOut; }
    public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }
    public boolean isRetryScheduled() { return retryScheduled; }
    public void setRetryScheduled(boolean retryScheduled) { this.retryScheduled = retryScheduled; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
