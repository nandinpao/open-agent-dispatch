package com.opensocket.aievent.database.persistence.a2a.operations;

import java.time.OffsetDateTime;

/** Persistence row for the P1B A2A Operations list projection. */
public class A2AOperationsListItemPo {
    private String requestId;
    private String rootTaskId;
    private String sourceTaskId;
    private String childTaskId;
    private String sourceDomainId;
    private String targetDomainId;
    private String requestedTaskType;
    private String requestStatus;
    private String operationalStage;
    private String childTaskStatus;
    private String dispatchStatus;
    private String runtimeStatus;
    private String resultStatus;
    private String cancellationStatus;
    private String aggregationStatus;
    private String blockerCode;
    private String blockerMessage;
    private String recommendedAction;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private long version;

    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getRootTaskId() { return rootTaskId; }
    public void setRootTaskId(String value) { rootTaskId = value; }
    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String value) { sourceTaskId = value; }
    public String getChildTaskId() { return childTaskId; }
    public void setChildTaskId(String value) { childTaskId = value; }
    public String getSourceDomainId() { return sourceDomainId; }
    public void setSourceDomainId(String value) { sourceDomainId = value; }
    public String getTargetDomainId() { return targetDomainId; }
    public void setTargetDomainId(String value) { targetDomainId = value; }
    public String getRequestedTaskType() { return requestedTaskType; }
    public void setRequestedTaskType(String value) { requestedTaskType = value; }
    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String value) { requestStatus = value; }
    public String getOperationalStage() { return operationalStage; }
    public void setOperationalStage(String value) { operationalStage = value; }
    public String getChildTaskStatus() { return childTaskStatus; }
    public void setChildTaskStatus(String value) { childTaskStatus = value; }
    public String getDispatchStatus() { return dispatchStatus; }
    public void setDispatchStatus(String value) { dispatchStatus = value; }
    public String getRuntimeStatus() { return runtimeStatus; }
    public void setRuntimeStatus(String value) { runtimeStatus = value; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String value) { resultStatus = value; }
    public String getCancellationStatus() { return cancellationStatus; }
    public void setCancellationStatus(String value) { cancellationStatus = value; }
    public String getAggregationStatus() { return aggregationStatus; }
    public void setAggregationStatus(String value) { aggregationStatus = value; }
    public String getBlockerCode() { return blockerCode; }
    public void setBlockerCode(String value) { blockerCode = value; }
    public String getBlockerMessage() { return blockerMessage; }
    public void setBlockerMessage(String value) { blockerMessage = value; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String value) { recommendedAction = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
}
