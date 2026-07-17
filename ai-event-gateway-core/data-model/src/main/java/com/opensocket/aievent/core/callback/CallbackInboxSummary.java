package com.opensocket.aievent.core.callback;

public class CallbackInboxSummary {
    private String taskId;
    private String dispatchRequestId;
    private int totalCallbacks;
    private int acceptedCallbacks;
    private int rejectedCallbacks;
    private int duplicateCallbacks;
    private int replayRejectedCallbacks;
    private String latestCallbackId;
    private String latestCallbackType;
    private String latestProcessStatus;
    private boolean terminalCallbackReceived;
    private boolean recoveryRequired;
    private String nextAction;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public int getTotalCallbacks() { return totalCallbacks; }
    public void setTotalCallbacks(int totalCallbacks) { this.totalCallbacks = totalCallbacks; }
    public int getAcceptedCallbacks() { return acceptedCallbacks; }
    public void setAcceptedCallbacks(int acceptedCallbacks) { this.acceptedCallbacks = acceptedCallbacks; }
    public int getRejectedCallbacks() { return rejectedCallbacks; }
    public void setRejectedCallbacks(int rejectedCallbacks) { this.rejectedCallbacks = rejectedCallbacks; }
    public int getDuplicateCallbacks() { return duplicateCallbacks; }
    public void setDuplicateCallbacks(int duplicateCallbacks) { this.duplicateCallbacks = duplicateCallbacks; }
    public int getReplayRejectedCallbacks() { return replayRejectedCallbacks; }
    public void setReplayRejectedCallbacks(int replayRejectedCallbacks) { this.replayRejectedCallbacks = replayRejectedCallbacks; }
    public String getLatestCallbackId() { return latestCallbackId; }
    public void setLatestCallbackId(String latestCallbackId) { this.latestCallbackId = latestCallbackId; }
    public String getLatestCallbackType() { return latestCallbackType; }
    public void setLatestCallbackType(String latestCallbackType) { this.latestCallbackType = latestCallbackType; }
    public String getLatestProcessStatus() { return latestProcessStatus; }
    public void setLatestProcessStatus(String latestProcessStatus) { this.latestProcessStatus = latestProcessStatus; }
    public boolean isTerminalCallbackReceived() { return terminalCallbackReceived; }
    public void setTerminalCallbackReceived(boolean terminalCallbackReceived) { this.terminalCallbackReceived = terminalCallbackReceived; }
    public boolean isRecoveryRequired() { return recoveryRequired; }
    public void setRecoveryRequired(boolean recoveryRequired) { this.recoveryRequired = recoveryRequired; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
}
